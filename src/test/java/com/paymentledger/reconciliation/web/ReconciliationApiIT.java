package com.paymentledger.reconciliation.web;

import static com.paymentledger.reconciliation.domain.DiscrepancyType.AMOUNT_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.DUPLICATE_IN_STATEMENT;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.LEDGER_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.MISSING_IN_SERVICE;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.MISSING_IN_STATEMENT;
import static com.paymentledger.reconciliation.domain.OperationType.CAPTURE;
import static com.paymentledger.reconciliation.web.StatementUploads.statement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.paymentledger.IntegrationTest;
import com.paymentledger.ledger.application.LedgerService;
import com.paymentledger.ledger.domain.AccountType;
import com.paymentledger.ledger.domain.EntryReference;
import com.paymentledger.ledger.domain.Posting;
import com.paymentledger.payment.application.PaymentBookkeeping;
import com.paymentledger.payment.application.PaymentService;
import com.paymentledger.payment.domain.Payment;
import com.paymentledger.reconciliation.domain.Discrepancy;
import com.paymentledger.shared.domain.Money;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ReconciliationApiIT extends IntegrationTest {

  private static final String HEADER = "psp_reference,type,amount_minor,currency,settled_on\n";
  private static final String TODAY = "2026-10-12";
  private static final String TOMORROW = "2026-10-13";
  private static final Currency BRL = Currency.getInstance("BRL");

  @Autowired PaymentService payments;
  @Autowired LedgerService ledger;

  @Test
  void reconcilesADayOfSettlements() {
    clock.advance(Duration.ofDays(-1));
    payments.capture("m1", Money.of(4_000, "BRL"), "psp-yesterday");
    clock.advance(Duration.ofDays(1));
    Payment settled = payments.capture("m1", Money.of(10_000, "BRL"), "psp-1");
    payments.refund(settled.id(), 2_500, "psp-r1");
    payments.capture("m1", Money.of(5_000, "BRL"), "psp-unsettled");
    payments.capture("m2", Money.of(7_000, "BRL"), "psp-short");

    var response =
        reconcile(
            HEADER
                + "psp-yesterday,CAPTURE,4000,BRL,2026-10-12\n"
                + "psp-1,CAPTURE,10000,BRL,2026-10-12\n"
                + "psp-r1,REFUND,2500,BRL,2026-10-12\n"
                + "psp-short,CAPTURE,6900,BRL,2026-10-12\n"
                + "psp-unknown,CAPTURE,1000,BRL,2026-10-12\n"
                + "psp-1,CAPTURE,10000,BRL,2026-10-12\n",
            TODAY,
            TOMORROW);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ReconciliationResponse report = response.getBody();
    assertThat(report.statementLines()).isEqualTo(6);
    assertThat(report.matched()).isEqualTo(3);
    assertThat(report.discrepancies())
        .extracting(Discrepancy::type, Discrepancy::pspReference)
        .containsExactlyInAnyOrder(
            tuple(DUPLICATE_IN_STATEMENT, "psp-1"),
            tuple(AMOUNT_MISMATCH, "psp-short"),
            tuple(MISSING_IN_SERVICE, "psp-unknown"),
            tuple(MISSING_IN_STATEMENT, "psp-unsettled"));
    assertThat(response.getHeaders().getLocation()).hasPath("/reconciliations/" + report.id());

    var stored = http.getForObject("/reconciliations/" + report.id(), ReconciliationResponse.class);
    assertThat(stored).isEqualTo(report);
  }

  /** Payments written without their entry, or booked with another amount, by a bug or by hand. */
  @Test
  void findsOperationsTheLedgerDoesNotBackUp() {
    insertPaymentWithoutBookkeeping("psp-unbooked", 3_000);
    UUID misbooked = insertPaymentWithoutBookkeeping("psp-misbooked", 3_000);
    UUID clearing = ledger.accountId(PaymentBookkeeping.PSP_CLEARING, AccountType.ASSET, BRL);
    UUID payable =
        ledger.accountId(PaymentBookkeeping.merchantPayableCode("m1"), AccountType.LIABILITY, BRL);
    ledger.post(
        new EntryReference(PaymentBookkeeping.CAPTURE, misbooked.toString()),
        List.of(
            Posting.debit(clearing, Money.of(1_000, "BRL")),
            Posting.credit(payable, Money.of(1_000, "BRL"))));

    var report =
        reconcile(
                HEADER
                    + "psp-unbooked,CAPTURE,3000,BRL,2026-10-12\n"
                    + "psp-misbooked,CAPTURE,3000,BRL,2026-10-12\n",
                TODAY,
                TOMORROW)
            .getBody();

    assertThat(report.matched()).isZero();
    assertThat(report.discrepancies())
        .containsExactlyInAnyOrder(
            new Discrepancy(
                LEDGER_MISMATCH,
                "psp-unbooked",
                CAPTURE,
                "no movement on PSP clearing in the ledger"),
            new Discrepancy(
                LEDGER_MISMATCH, "psp-misbooked", CAPTURE, "recorded 3000 BRL, ledger 1000 BRL"));
  }

  @Test
  void rejectsAMalformedStatementWithoutSavingARun() {
    var response = problem(HEADER + "psp-1,CAPTURE,ten,BRL,2026-10-12\n", TODAY, TOMORROW);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getDetail()).isEqualTo("Line 2: invalid amount_minor 'ten'");
    assertThat(count("reconciliation_runs")).isZero();
  }

  @Test
  void rejectsAnInvalidPeriod() {
    assertThat(problem(HEADER, TOMORROW, TODAY).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(problem(HEADER, "2026-01-01", "2026-03-01").getBody().getDetail())
        .isEqualTo("Period cannot exceed 31 days");
    assertThat(problem(HEADER, "12/10/2026", TOMORROW).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anUnknownRunIsNotFound() {
    var response = http.getForEntity("/reconciliations/" + UUID.randomUUID(), ProblemDetail.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private UUID insertPaymentWithoutBookkeeping(String pspReference, long amount) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into payments
                (id, merchant_id, amount_minor, fee_minor, currency, psp_reference, captured_at)
            values (?, 'm1', ?, 0, 'BRL', ?, ?)
            """)
        .params(id, amount, pspReference, Timestamp.from(clock.instant()))
        .update();
    return id;
  }

  private ResponseEntity<ReconciliationResponse> reconcile(String csv, String start, String end) {
    return http.postForEntity(uri(start, end), statement(csv), ReconciliationResponse.class);
  }

  private ResponseEntity<ProblemDetail> problem(String csv, String start, String end) {
    return http.postForEntity(uri(start, end), statement(csv), ProblemDetail.class);
  }

  private static String uri(String start, String end) {
    return "/reconciliations?periodStart=" + start + "&periodEnd=" + end;
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}

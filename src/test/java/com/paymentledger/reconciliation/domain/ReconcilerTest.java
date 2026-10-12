package com.paymentledger.reconciliation.domain;

import static com.paymentledger.reconciliation.domain.DiscrepancyType.AMOUNT_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.CURRENCY_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.DUPLICATE_IN_STATEMENT;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.LEDGER_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.MISSING_IN_SERVICE;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.MISSING_IN_STATEMENT;
import static com.paymentledger.reconciliation.domain.OperationType.CAPTURE;
import static com.paymentledger.reconciliation.domain.OperationType.REFUND;
import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReconcilerTest {

  private static final LocalDate DAY = LocalDate.parse("2026-10-12");

  @Test
  void matchesWhenStatementOperationAndLedgerAgree() {
    var report =
        Reconciler.reconcile(
            List.of(line(2, "p1", CAPTURE, 10_000, "BRL"), line(3, "r1", REFUND, 2_500, "BRL")),
            List.of(booked("p1", CAPTURE, 10_000), booked("r1", REFUND, 2_500)),
            List.of());

    assertThat(report.matched()).isEqualTo(2);
    assertThat(report.statementLines()).isEqualTo(2);
    assertThat(report.discrepancies()).isEmpty();
  }

  @Test
  void settlesAnOperationRecordedBeforeThePeriod() {
    var report =
        Reconciler.reconcile(
            List.of(line(2, "p0", CAPTURE, 10_000, "BRL")),
            List.of(),
            List.of(booked("p0", CAPTURE, 10_000)));

    assertThat(report.matched()).isEqualTo(1);
    assertThat(report.discrepancies()).isEmpty();
  }

  @Test
  void reportsALineTheServiceNeverRecorded() {
    var report =
        Reconciler.reconcile(List.of(line(2, "ghost", CAPTURE, 500, "BRL")), List.of(), List.of());

    assertThat(report.discrepancies())
        .containsExactly(new Discrepancy(MISSING_IN_SERVICE, "ghost", CAPTURE, "settled 500 BRL"));
  }

  @Test
  void reportsAnOperationThePspDidNotSettle() {
    var report = Reconciler.reconcile(List.of(), List.of(booked("p1", CAPTURE, 10_000)), List.of());

    assertThat(report.discrepancies())
        .containsExactly(
            new Discrepancy(MISSING_IN_STATEMENT, "p1", CAPTURE, "recorded 10000 BRL"));
  }

  @Test
  void anOperationOutsideThePeriodIsNotExpectedInTheStatement() {
    var report = Reconciler.reconcile(List.of(), List.of(), List.of(booked("p0", CAPTURE, 1)));

    assertThat(report.discrepancies()).isEmpty();
  }

  @Test
  void reportsDifferentAmountsAndCurrencies() {
    var report =
        Reconciler.reconcile(
            List.of(line(2, "p1", CAPTURE, 9_999, "BRL"), line(3, "p2", CAPTURE, 10_000, "USD")),
            List.of(booked("p1", CAPTURE, 10_000), booked("p2", CAPTURE, 10_000)),
            List.of());

    assertThat(report.matched()).isZero();
    assertThat(report.discrepancies())
        .containsExactly(
            new Discrepancy(AMOUNT_MISMATCH, "p1", CAPTURE, "settled 9999 BRL, recorded 10000 BRL"),
            new Discrepancy(
                CURRENCY_MISMATCH, "p2", CAPTURE, "settled 10000 USD, recorded 10000 BRL"));
  }

  @Test
  void aCaptureAndARefundMayShareAPspReference() {
    var report =
        Reconciler.reconcile(
            List.of(line(2, "p1", CAPTURE, 10_000, "BRL"), line(3, "p1", REFUND, 10_000, "BRL")),
            List.of(booked("p1", CAPTURE, 10_000), booked("p1", REFUND, 10_000)),
            List.of());

    assertThat(report.matched()).isEqualTo(2);
  }

  @Test
  void reportsTheLedgerWhenItDisagreesWithTheOperation() {
    var tampered =
        new BookedOperation(
            new OperationKey("p1", CAPTURE),
            Money.of(10_000, "BRL"),
            Optional.of(Money.of(1_000, "BRL")));
    var unbooked =
        new BookedOperation(new OperationKey("r1", REFUND), Money.of(500, "BRL"), Optional.empty());

    var report =
        Reconciler.reconcile(
            List.of(line(2, "p1", CAPTURE, 10_000, "BRL"), line(3, "r1", REFUND, 500, "BRL")),
            List.of(tampered, unbooked),
            List.of());

    assertThat(report.matched()).isZero();
    assertThat(report.discrepancies())
        .containsExactly(
            new Discrepancy(LEDGER_MISMATCH, "p1", CAPTURE, "recorded 10000 BRL, ledger 1000 BRL"),
            new Discrepancy(
                LEDGER_MISMATCH, "r1", REFUND, "no movement on PSP clearing in the ledger"));
  }

  @Test
  void reportsTheLedgerOnceEvenWhenTheStatementAlsoDisagrees() {
    var tampered =
        new BookedOperation(
            new OperationKey("p1", CAPTURE), Money.of(10_000, "BRL"), Optional.empty());

    var report =
        Reconciler.reconcile(
            List.of(line(2, "p1", CAPTURE, 1, "BRL")), List.of(tampered), List.of(tampered));

    assertThat(report.discrepancies())
        .extracting(Discrepancy::type)
        .containsExactly(LEDGER_MISMATCH, AMOUNT_MISMATCH);
  }

  @Test
  void countsARepeatedLineOnceAndReportsTheRepeat() {
    var report =
        Reconciler.reconcile(
            List.of(line(2, "p1", CAPTURE, 10_000, "BRL"), line(7, "p1", CAPTURE, 10_000, "BRL")),
            List.of(booked("p1", CAPTURE, 10_000)),
            List.of());

    assertThat(report.matched()).isEqualTo(1);
    assertThat(report.discrepancies())
        .containsExactly(
            new Discrepancy(DUPLICATE_IN_STATEMENT, "p1", CAPTURE, "line 7 repeats line 2"));
  }

  private static StatementLine line(
      int number, String reference, OperationType type, long amount, String currency) {
    return new StatementLine(number, reference, type, Money.of(amount, currency), DAY);
  }

  private static BookedOperation booked(String reference, OperationType type, long amount) {
    Money money = Money.of(amount, "BRL");
    return new BookedOperation(new OperationKey(reference, type), money, Optional.of(money));
  }
}

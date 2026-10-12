package com.paymentledger.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentledger.IntegrationTest;
import com.paymentledger.outbox.application.OutboxRecorder;
import com.paymentledger.payment.domain.Payment;
import com.paymentledger.shared.domain.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Payment state, ledger entries and outbox events commit or roll back as one. */
class PaymentOutboxIT extends IntegrationTest {

  @Autowired PaymentService payments;
  @Autowired OutboxRecorder recorder;
  @Autowired TransactionTemplate tx;
  @Autowired ObjectMapper json;

  @Test
  void captureAndRefundEachRecordOneEvent() throws Exception {
    Payment payment = payments.capture("m1", Money.of(10_000, "BRL"), "psp-1");
    payments.refund(payment.id(), 2_500, "psp-r1");

    var events =
        jdbc.sql("select event_type, payload from outbox_events order by position")
            .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)})
            .list();
    assertThat(events).extracting(e -> e[0]).containsExactly("PaymentCaptured", "PaymentRefunded");

    JsonNode refunded = json.readTree(events.get(1)[1]);
    assertThat(refunded.get("paymentId").asText()).isEqualTo(payment.id().toString());
    assertThat(refunded.get("amountMinor").asLong()).isEqualTo(2_500);
    assertThat(refunded.get("feeReturnedMinor").asLong()).isEqualTo(72);
  }

  @Test
  void aFailureAfterTheEventIsWrittenLeavesNoTrace() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      payments.capture("m1", Money.of(10_000, "BRL"), "psp-1");
                      throw new IllegalStateException("crash before commit");
                    }))
        .hasMessage("crash before commit");

    assertThat(count("payments")).isZero();
    assertThat(count("journal_entries")).isZero();
    assertThat(count("outbox_events")).isZero();
  }

  @Test
  void anEventCannotBeRecordedOutsideATransaction() {
    assertThatThrownBy(() -> recorder.record("Test", UUID.randomUUID(), "x"))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}

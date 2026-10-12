package com.paymentledger;

import static com.paymentledger.LedgerAssertions.assertLedgerConsistent;
import static com.paymentledger.LedgerAssertions.shardedBalance;
import static com.paymentledger.reconciliation.web.StatementUploads.statement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.payment.domain.PaymentStatus;
import com.paymentledger.payment.web.PaymentResponse;
import com.paymentledger.payment.web.RefundResponse;
import com.paymentledger.reconciliation.web.ReconciliationResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/** One payment through every part of the service, over HTTP, with the relay on its schedule. */
@TestPropertySource(properties = {"scheduling.enabled=true", "outbox.relay.poll-interval=100ms"})
class PaymentLedgerFlowE2E extends IntegrationTest {

  @Test
  void captureRefundPublishAndReconcile() {
    PaymentResponse payment =
        post(
            "/payments",
            "capture-1",
            Map.of(
                "merchantId", "merchant-42",
                "amountMinor", 15_990,
                "currency", "BRL",
                "pspReference", "ch_3Q8xYz"),
            PaymentResponse.class);
    RefundResponse refund =
        post(
            "/payments/" + payment.id() + "/refunds",
            "refund-1",
            Map.of("amountMinor", 5_000, "pspReference", "re_1Pq7Ab"),
            RefundResponse.class);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(publisher.published())
                    .extracting(OutboxEvent::eventType)
                    .containsExactly("PaymentCaptured", "PaymentRefunded"));

    String csv =
        """
        psp_reference,type,amount_minor,currency,settled_on
        ch_3Q8xYz,CAPTURE,15990,BRL,2026-10-12
        re_1Pq7Ab,REFUND,5000,BRL,2026-10-12
        """;
    var reconciliation =
        http.postForEntity(
            "/reconciliations?periodStart=2026-10-12&periodEnd=2026-10-13",
            statement(csv),
            ReconciliationResponse.class);

    assertThat(reconciliation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(reconciliation.getBody().matched()).isEqualTo(2);
    assertThat(reconciliation.getBody().discrepancies()).isEmpty();

    PaymentResponse current = http.getForObject("/payments/" + payment.id(), PaymentResponse.class);
    assertThat(current.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    assertThat(current.refunds()).containsExactly(refund);
    assertThat(merchantPayable()).isEqualTo(15_990 - 464 - (5_000 - 145));
    assertLedgerConsistent(jdbc);
  }

  private <T> T post(String path, String idempotencyKey, Object body, Class<T> type) {
    var headers = new HttpHeaders();
    headers.set("Idempotency-Key", idempotencyKey);
    var response = http.postForEntity(path, new HttpEntity<>(body, headers), type);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  private long merchantPayable() {
    return shardedBalance(jdbc, "MERCHANT_PAYABLE:merchant-42");
  }
}

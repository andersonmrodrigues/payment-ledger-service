package com.paymentledger.payment.web;

import static com.paymentledger.LedgerAssertions.assertLedgerConsistent;
import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class PaymentApiIT extends IntegrationTest {

  private static final CapturePaymentRequest CAPTURE =
      new CapturePaymentRequest("m1", 10_000, "BRL", "psp-1");

  @Test
  void capturesAndReadsAPayment() {
    var created = capture("key-1", CAPTURE, PaymentResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
    PaymentResponse body = created.getBody();
    assertThat(body.feeMinor()).isEqualTo(290);

    var read = http.getForEntity("/payments/" + body.id(), PaymentResponse.class);
    assertThat(read.getBody()).isEqualTo(body);
  }

  @Test
  void replayingAKeyReturnsTheOriginalResponseAndBooksNothingNew() {
    var first = capture("key-1", CAPTURE, PaymentResponse.class);
    var second = capture("key-1", CAPTURE, PaymentResponse.class);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    assertThat(second.getBody()).isEqualTo(first.getBody());
    assertThat(count("payments")).isEqualTo(1);
    assertThat(count("journal_entries")).isEqualTo(1);
  }

  @Test
  void reusingAKeyWithADifferentBodyIsRejected() {
    capture("key-1", CAPTURE, PaymentResponse.class);

    var reused =
        capture(
            "key-1", new CapturePaymentRequest("m1", 9_999, "BRL", "psp-2"), ProblemDetail.class);

    assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(reused.getBody().getDetail()).contains("different request");
    assertThat(count("payments")).isEqualTo(1);
  }

  @Test
  void aReusedPspReferenceUnderANewKeyIsAConflict() {
    capture("key-1", CAPTURE, PaymentResponse.class);

    var duplicate = capture("key-2", CAPTURE, ProblemDetail.class);

    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(count("journal_entries")).isEqualTo(1);
  }

  /** Twenty clients retrying the same request at the same instant produce exactly one payment. */
  @Test
  void concurrentRequestsWithTheSameKeyCaptureOnce() throws Exception {
    int threads = 20;
    var start = new CountDownLatch(1);
    List<Future<ResponseEntity<PaymentResponse>>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return capture("same-key", CAPTURE, PaymentResponse.class);
                }));
      }
      start.countDown();
    }

    var ids = new HashSet<UUID>();
    for (Future<ResponseEntity<PaymentResponse>> result : results) {
      ResponseEntity<PaymentResponse> response = result.get();
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      ids.add(response.getBody().id());
    }
    assertThat(ids).hasSize(1);
    assertThat(count("payments")).isEqualTo(1);
    assertThat(count("journal_entries")).isEqualTo(1);
    assertLedgerConsistent(jdbc);
  }

  @Test
  void aFailedRefundDoesNotConsumeItsKey() {
    UUID paymentId = capture("key-1", CAPTURE, PaymentResponse.class).getBody().id();

    var tooMuch =
        refund(paymentId, "refund-key", new RefundRequest(10_001, "r1"), ProblemDetail.class);
    assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(count("idempotency_keys")).isEqualTo(1);

    var corrected =
        refund(paymentId, "refund-key", new RefundRequest(10_000, "r1"), RefundResponse.class);
    assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(corrected.getBody().feeReturnedMinor()).isEqualTo(290);
    assertLedgerConsistent(jdbc);
  }

  @Test
  void theSameRefundKeyOnAnotherPaymentIsADifferentRequest() {
    UUID first = capture("key-1", CAPTURE, PaymentResponse.class).getBody().id();
    UUID second =
        capture(
                "key-2",
                new CapturePaymentRequest("m1", 10_000, "BRL", "psp-2"),
                PaymentResponse.class)
            .getBody()
            .id();
    refund(first, "refund-key", new RefundRequest(100, "r1"), RefundResponse.class);

    var other = refund(second, "refund-key", new RefundRequest(100, "r1"), ProblemDetail.class);

    assertThat(other.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void rejectsInvalidRequests() {
    assertThat(
            capture("k", new CapturePaymentRequest("m1", 0, "BRL", "p"), ProblemDetail.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            capture("k", new CapturePaymentRequest("m1", 100, "ZZZ", "p"), ProblemDetail.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            capture("k", new CapturePaymentRequest("bad id", 100, "BRL", "p"), ProblemDetail.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            http.postForEntity("/payments", Map.of("merchantId", "m1"), ProblemDetail.class)
                .getStatusCode())
        .as("missing Idempotency-Key")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(capture("x".repeat(256), CAPTURE, ProblemDetail.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            http.getForEntity("/payments/" + UUID.randomUUID(), ProblemDetail.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(count("payments")).isZero();
  }

  private <T> ResponseEntity<T> capture(String key, CapturePaymentRequest body, Class<T> type) {
    return http.postForEntity("/payments", withKey(key, body), type);
  }

  private <T> ResponseEntity<T> refund(
      UUID paymentId, String key, RefundRequest body, Class<T> type) {
    return http.postForEntity("/payments/" + paymentId + "/refunds", withKey(key, body), type);
  }

  private static <B> HttpEntity<B> withKey(String key, B body) {
    var headers = new HttpHeaders();
    headers.set("Idempotency-Key", key);
    return new HttpEntity<>(body, headers);
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}

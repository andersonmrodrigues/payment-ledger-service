package com.paymentledger.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class IdempotencyRetentionIT extends IntegrationTest {

  @Autowired IdempotencyRetention retention;

  @Test
  void forgetsKeysPastTheRetentionPeriod() {
    for (int i = 1; i <= 3; i++) {
      assertThat(capture("old-" + i, "psp-old-" + i)).isEqualTo(HttpStatus.CREATED);
    }
    clock.advance(Duration.ofDays(6));
    assertThat(capture("recent", "psp-recent")).isEqualTo(HttpStatus.CREATED);
    clock.advance(Duration.ofDays(2));

    retention.purge();

    assertThat(jdbc.sql("select key from idempotency_keys").query(String.class).list())
        .containsExactly("recent");
  }

  @Test
  void aForgottenKeyCannotDuplicateACaptureBecauseThePspReferenceIsUnique() {
    capture("old-1", "psp-old-1");
    clock.advance(Duration.ofDays(8));
    retention.purge();

    assertThat(capture("old-1", "psp-old-1")).isEqualTo(HttpStatus.CONFLICT);
    assertThat(jdbc.sql("select count(*) from payments").query(Long.class).single()).isEqualTo(1);
  }

  private HttpStatus capture(String key, String pspReference) {
    var headers = new HttpHeaders();
    headers.set("Idempotency-Key", key);
    var body =
        Map.of(
            "merchantId",
            "m1",
            "amountMinor",
            1_000,
            "currency",
            "BRL",
            "pspReference",
            pspReference);
    return HttpStatus.valueOf(
        http.postForEntity("/payments", new HttpEntity<>(body, headers), String.class)
            .getStatusCode()
            .value());
  }
}

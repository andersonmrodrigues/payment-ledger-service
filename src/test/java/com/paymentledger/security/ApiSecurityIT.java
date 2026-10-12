package com.paymentledger.security;

import static com.paymentledger.LedgerAssertions.shardedBalance;
import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ApiSecurityIT extends IntegrationTest {

  @Test
  void callsWithoutAValidKeyGet401AsProblemDetails() {
    var noKey = anonymous.getForEntity("/payments/" + someId(), String.class);
    assertThat(noKey.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(noKey.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    assertThat(noKey.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(as("wrong-key").getForEntity("/payments/" + someId(), ProblemDetail.class))
        .satisfies(response -> assertUnauthorized(response, "/payments/" + someId()));
    assertThat(withAuthorization("Basic Y2hlY2tvdXQ6eA==").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(withAuthorization("Bearer").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void aRejectedCaptureBooksNothing() {
    var response = anonymous.postForEntity("/payments", capture("psp-anon"), ProblemDetail.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(jdbc.sql("select count(*) from payments").query(Long.class).single()).isZero();
  }

  @Test
  void eachRoleReachesOnlyItsEndpoints() {
    assertThat(
            http.postForEntity("/outbox/replay", Map.of("eventIds", List.of()), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.getForEntity("/reconciliations/" + someId(), String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    var forbidden = backOffice.postForEntity("/payments", capture("psp-bo"), ProblemDetail.class);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(forbidden.getBody().getDetail())
        .isEqualTo("This API key may not call this endpoint");
    assertThat(
            backOffice.getForEntity("/reconciliations/" + someId(), String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anEndpointNoRuleNamesIsClosedToEveryone() {
    assertThat(http.getForEntity("/anything", String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(anonymous.getForEntity("/anything", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void aClientCannotSeeOrRefundAnotherClientsPayment() {
    var created = http.postForEntity("/payments", capture("psp-1"), Map.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String paymentId = (String) created.getBody().get("id");
    TestRestTemplate marketplace = as(MARKETPLACE_KEY);

    assertThat(
            marketplace.getForEntity("/payments/" + paymentId, ProblemDetail.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var refund =
        marketplace.postForEntity(
            "/payments/" + paymentId + "/refunds",
            withKey("refund-1", Map.of("amountMinor", 1_000, "pspReference", "psp-r1")),
            ProblemDetail.class);
    assertThat(refund.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(jdbc.sql("select count(*) from refunds").query(Long.class).single()).isZero();
    assertThat(shardedBalance(jdbc, "PSP_CLEARING")).isEqualTo(10_000);

    assertThat(http.getForEntity("/payments/" + paymentId, Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void idempotencyKeysAreScopedToTheClient() {
    var first = http.postForEntity("/payments", capture("psp-1"), Map.class);
    var second =
        as(MARKETPLACE_KEY)
            .postForEntity("/payments", withKey("same-key", body("psp-2", 2_000)), Map.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
    assertThat(second.getBody().get("id")).isNotEqualTo(first.getBody().get("id"));
    assertThat(
            jdbc.sql("select client_id from payments order by client_id")
                .query(String.class)
                .list())
        .containsExactly(CHECKOUT, "marketplace");

    var replay = http.postForEntity("/payments", capture("psp-1"), Map.class);
    assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    assertThat(replay.getBody().get("id")).isEqualTo(first.getBody().get("id"));
  }

  private static HttpEntity<Map<String, Object>> capture(String pspReference) {
    return withKey("same-key", body(pspReference, 10_000));
  }

  private static Map<String, Object> body(String pspReference, long amountMinor) {
    return Map.of(
        "merchantId",
        "m1",
        "amountMinor",
        amountMinor,
        "currency",
        "BRL",
        "pspReference",
        pspReference);
  }

  private static <T> HttpEntity<T> withKey(String key, T body) {
    var headers = new HttpHeaders();
    headers.set("Idempotency-Key", key);
    return new HttpEntity<>(body, headers);
  }

  private ResponseEntity<String> withAuthorization(String value) {
    var headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, value);
    return anonymous.exchange(
        "/payments/" + someId(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private static void assertUnauthorized(ResponseEntity<ProblemDetail> response, String path) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().getStatus()).isEqualTo(401);
    assertThat(response.getBody().getDetail()).isEqualTo("A valid API key is required");
    assertThat(response.getBody().getInstance()).hasToString(path);
  }

  private static String someId() {
    return "8d7b6c1e-3f2a-4b5c-9d8e-7f6a5b4c3d2e";
  }
}

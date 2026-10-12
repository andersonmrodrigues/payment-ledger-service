package com.paymentledger.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs an operation at most once per idempotency key.
 *
 * <p>The key is claimed in the same transaction as the operation. A concurrent request with the
 * same key blocks on the claim until the first transaction ends: if it committed, the stored
 * response is returned; if it rolled back, the waiting request runs the operation itself. A failed
 * operation therefore leaves no trace and can be retried with the same key.
 */
@Component
public class IdempotentExecutor {

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final Clock clock;

  public IdempotentExecutor(
      JdbcClient jdbc, TransactionTemplate tx, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.json = json;
    this.clock = clock;
  }

  /**
   * @param request what identifies the request besides the key (path and body); the same key with a
   *     different request is rejected
   */
  public <T> IdempotentResult<T> execute(
      String scope, String key, Object request, Class<T> responseType, Supplier<T> operation) {
    String requestHash = sha256(toJson(request));
    return tx.execute(
        status -> {
          if (claim(scope, key, requestHash)) {
            T response = operation.get();
            storeResponse(scope, key, toJson(response));
            return new IdempotentResult<>(response, false);
          }
          return new IdempotentResult<>(
              storedResponse(scope, key, requestHash, responseType), true);
        });
  }

  private boolean claim(String scope, String key, String requestHash) {
    return jdbc.sql(
                """
                insert into idempotency_keys (scope, key, request_hash, created_at)
                values (?, ?, ?, ?)
                on conflict (scope, key) do nothing
                """)
            .params(scope, key, requestHash, Timestamp.from(clock.instant()))
            .update()
        == 1;
  }

  private void storeResponse(String scope, String key, String body) {
    jdbc.sql("update idempotency_keys set response_body = ? where scope = ? and key = ?")
        .params(body, scope, key)
        .update();
  }

  private <T> T storedResponse(String scope, String key, String requestHash, Class<T> type) {
    record Stored(String requestHash, String responseBody) {}
    Stored stored =
        jdbc.sql(
                """
                select request_hash, response_body from idempotency_keys
                where scope = ? and key = ?
                """)
            .params(scope, key)
            .query((rs, row) -> new Stored(rs.getString(1), rs.getString(2)))
            .single();
    if (!stored.requestHash().equals(requestHash)) {
      throw new IdempotencyKeyReusedException(key);
    }
    try {
      return json.readValue(stored.responseBody(), type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored response for key " + key + " is unreadable", e);
    }
  }

  private String toJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize " + value.getClass(), e);
    }
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JVM", e);
    }
  }
}

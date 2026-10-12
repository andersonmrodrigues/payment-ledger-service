package com.paymentledger.outbox.application;

import com.paymentledger.outbox.domain.RetryPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("outbox")
public record OutboxProperties(Relay relay, Retry retry, Retention retention) {

  /**
   * @param lease how long a claimed batch is reserved for one relay; must comfortably exceed the
   *     time to publish a whole batch, or a slow batch is claimed and sent again by another relay
   */
  public record Relay(boolean enabled, Duration pollInterval, int batchSize, Duration lease) {}

  public record Retry(Duration baseDelay, Duration maxDelay, int maxAttempts) {}

  /** Published events older than {@code period} are deleted, {@code batchSize} rows at a time. */
  public record Retention(Duration period, int batchSize) {}

  public RetryPolicy retryPolicy() {
    return new RetryPolicy(retry.baseDelay(), retry.maxDelay(), retry.maxAttempts());
  }
}

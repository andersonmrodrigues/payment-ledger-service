package com.paymentledger.outbox.application;

import com.paymentledger.outbox.domain.RetryPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("outbox")
public record OutboxProperties(Relay relay, Retry retry) {

  public record Relay(boolean enabled, Duration pollInterval, int batchSize) {}

  public record Retry(Duration baseDelay, Duration maxDelay, int maxAttempts) {}

  public RetryPolicy retryPolicy() {
    return new RetryPolicy(retry.baseDelay(), retry.maxDelay(), retry.maxAttempts());
  }
}

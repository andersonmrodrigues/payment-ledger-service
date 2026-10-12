package com.paymentledger.outbox.domain;

import java.time.Duration;
import java.util.Objects;

/** Exponential backoff: base, 2 x base, 4 x base ... capped at maxDelay, for maxAttempts tries. */
public record RetryPolicy(Duration baseDelay, Duration maxDelay, int maxAttempts) {

  public RetryPolicy {
    Objects.requireNonNull(baseDelay, "baseDelay");
    Objects.requireNonNull(maxDelay, "maxDelay");
    if (baseDelay.isNegative() || baseDelay.isZero() || maxDelay.compareTo(baseDelay) < 0) {
      throw new IllegalArgumentException("Need 0 < baseDelay <= maxDelay");
    }
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
  }

  /** The wait after the given failed attempt (1-based). */
  public Duration delayAfter(int failedAttempts) {
    int doublings = Math.min(failedAttempts - 1, 30);
    Duration delay = baseDelay.multipliedBy(1L << doublings);
    return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
  }

  public boolean exhausted(int failedAttempts) {
    return failedAttempts >= maxAttempts;
  }
}

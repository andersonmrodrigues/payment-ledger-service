package com.paymentledger.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  private final RetryPolicy policy =
      new RetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(10), 5);

  @Test
  void doublesTheDelayUpToTheCap() {
    assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(2));
    assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofSeconds(4));
    assertThat(policy.delayAfter(4)).isEqualTo(Duration.ofSeconds(8));
    assertThat(policy.delayAfter(5)).isEqualTo(Duration.ofSeconds(10));
    assertThat(policy.delayAfter(500)).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void isExhaustedAtMaxAttempts() {
    assertThat(policy.exhausted(4)).isFalse();
    assertThat(policy.exhausted(5)).isTrue();
  }

  @Test
  void rejectsNonsensicalSettings() {
    assertThatThrownBy(() -> new RetryPolicy(Duration.ZERO, Duration.ofSeconds(1), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RetryPolicy(Duration.ofSeconds(2), Duration.ofSeconds(1), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(1), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

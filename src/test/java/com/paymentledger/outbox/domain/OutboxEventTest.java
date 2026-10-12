package com.paymentledger.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
  private final RetryPolicy policy =
      new RetryPolicy(Duration.ofSeconds(1), Duration.ofMinutes(1), 2);
  private final OutboxEvent event =
      OutboxEvent.pending("Payment", UUID.randomUUID(), "E", "{}", NOW);

  @Test
  void aFailureSchedulesTheNextAttemptWithBackoff() {
    OutboxEvent failed = event.failed("boom", NOW, policy);

    assertThat(failed.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(failed.attempts()).isEqualTo(1);
    assertThat(failed.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
    assertThat(failed.lastError()).isEqualTo("boom");
    assertThat(failed.id()).isEqualTo(event.id());
  }

  @Test
  void theLastAllowedFailureMakesTheEventDead() {
    OutboxEvent dead = event.failed("1", NOW, policy).failed("2", NOW, policy);

    assertThat(dead.status()).isEqualTo(OutboxStatus.DEAD);
    assertThat(dead.attempts()).isEqualTo(2);
  }

  @Test
  void publishingRecordsTheAttemptAndClearsTheError() {
    OutboxEvent published = event.failed("boom", NOW, policy).published(NOW.plusSeconds(1));

    assertThat(published.status()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(published.attempts()).isEqualTo(2);
    assertThat(published.lastError()).isNull();
    assertThat(published.publishedAt()).isEqualTo(NOW.plusSeconds(1));
  }
}

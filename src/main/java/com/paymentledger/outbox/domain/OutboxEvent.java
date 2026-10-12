package com.paymentledger.outbox.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An event waiting to leave the service. Its id never changes across retries and replays, so
 * consumers can discard duplicates: delivery is at least once.
 */
public record OutboxEvent(
    UUID id,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    String payload,
    OutboxStatus status,
    int attempts,
    Instant nextAttemptAt,
    String lastError,
    Instant createdAt,
    Instant publishedAt) {

  public OutboxEvent {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
  }

  public static OutboxEvent pending(
      String aggregateType, UUID aggregateId, String eventType, String payload, Instant now) {
    return new OutboxEvent(
        UUID.randomUUID(),
        aggregateType,
        aggregateId,
        eventType,
        payload,
        OutboxStatus.PENDING,
        0,
        now,
        null,
        now,
        null);
  }

  public OutboxEvent published(Instant now) {
    return new OutboxEvent(
        id,
        aggregateType,
        aggregateId,
        eventType,
        payload,
        OutboxStatus.PUBLISHED,
        attempts + 1,
        nextAttemptAt,
        null,
        createdAt,
        now);
  }

  /** Schedules the next try, or gives up once the policy is exhausted. */
  public OutboxEvent failed(String error, Instant now, RetryPolicy policy) {
    int failedAttempts = attempts + 1;
    boolean dead = policy.exhausted(failedAttempts);
    return new OutboxEvent(
        id,
        aggregateType,
        aggregateId,
        eventType,
        payload,
        dead ? OutboxStatus.DEAD : OutboxStatus.PENDING,
        failedAttempts,
        dead ? nextAttemptAt : now.plus(policy.delayAfter(failedAttempts)),
        error,
        createdAt,
        null);
  }
}

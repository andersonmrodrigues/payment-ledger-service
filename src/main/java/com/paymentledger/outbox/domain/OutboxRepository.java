package com.paymentledger.outbox.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

  void add(OutboxEvent event);

  /**
   * Locks up to {@code limit} due pending events, skipping rows another relay holds, so parallel
   * relays never work on the same event.
   */
  List<OutboxEvent> claimDue(Instant now, int limit);

  void update(OutboxEvent event);

  /** Makes the events pending again with fresh attempts; returns how many were requeued. */
  int requeue(Collection<UUID> ids, Instant now);

  /** Requeues every event created in [from, to); returns how many were requeued. */
  int requeueCreatedBetween(Instant from, Instant to, Instant now);
}

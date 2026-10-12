package com.paymentledger.outbox.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

  void add(OutboxEvent event);

  /**
   * Leases up to {@code limit} due pending events to the caller until {@code leaseUntil}: they stay
   * pending but are not due again before then. Rows another relay is claiming are skipped. Returns
   * them in the order they were recorded.
   */
  List<OutboxEvent> claimDue(Instant now, Instant leaseUntil, UUID lease, int limit);

  /**
   * Records the outcome of a leased event. Returns false, changing nothing, when the lease is no
   * longer the caller's: it expired and another relay claimed the event, or it was replayed.
   */
  boolean complete(OutboxEvent outcome, UUID lease);

  /** Makes the events pending again with fresh attempts; returns how many were requeued. */
  int requeue(Collection<UUID> ids, Instant now);

  /** Requeues every event created in [from, to); returns how many were requeued. */
  int requeueCreatedBetween(Instant from, Instant to, Instant now);

  /** Deletes up to {@code limit} events published before {@code cutoff}; returns the count. */
  int deletePublishedBefore(Instant cutoff, int limit);
}

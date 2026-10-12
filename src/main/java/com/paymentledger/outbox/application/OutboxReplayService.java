package com.paymentledger.outbox.application;

import com.paymentledger.outbox.domain.OutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends events again with their original ids: dead events after the downstream is fixed, or a time
 * window of already published events for a consumer that lost its data.
 */
@Service
public class OutboxReplayService {

  private final OutboxRepository outbox;
  private final Clock clock;

  public OutboxReplayService(OutboxRepository outbox, Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public int replay(Collection<UUID> eventIds) {
    return outbox.requeue(eventIds, clock.instant());
  }

  @Transactional
  public int replayCreatedBetween(Instant from, Instant to) {
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("from must be before to");
    }
    return outbox.requeueCreatedBetween(from, to, clock.instant());
  }
}

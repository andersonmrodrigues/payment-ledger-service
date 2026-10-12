package com.paymentledger.outbox.application;

import com.paymentledger.outbox.domain.EventPublisher;
import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.outbox.domain.OutboxRepository;
import com.paymentledger.outbox.domain.RetryPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Publishes due events one batch at a time.
 *
 * <p>No transaction or row lock is held while the broker is called: the batch is leased in one
 * statement, published, and each outcome is written on its own. One failing event therefore neither
 * blocks nor rolls back the others, and a relay that dies mid-batch only delays its events until
 * the lease runs out.
 */
@Service
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxRepository outbox;
  private final EventPublisher publisher;
  private final RetryPolicy retryPolicy;
  private final int batchSize;
  private final Duration lease;
  private final Clock clock;

  public OutboxRelay(
      OutboxRepository outbox, EventPublisher publisher, OutboxProperties properties, Clock clock) {
    this.outbox = outbox;
    this.publisher = publisher;
    this.retryPolicy = properties.retryPolicy();
    this.batchSize = properties.relay().batchSize();
    this.lease = properties.relay().lease();
    this.clock = clock;
  }

  /** Returns how many events were claimed, published or not. */
  public int publishDue() {
    Instant now = clock.instant();
    UUID token = UUID.randomUUID();
    List<OutboxEvent> claimed = outbox.claimDue(now, now.plus(lease), token, batchSize);
    for (OutboxEvent event : claimed) {
      if (!outbox.complete(attempt(event), token)) {
        log.warn(
            "Lease on event {} expired before its outcome was recorded; another relay owns it",
            event.id());
      }
    }
    return claimed.size();
  }

  private OutboxEvent attempt(OutboxEvent event) {
    try {
      publisher.publish(event);
      return event.published(clock.instant());
    } catch (RuntimeException e) {
      OutboxEvent failed =
          event.failed(String.valueOf(e.getMessage()), clock.instant(), retryPolicy);
      log.warn(
          "Publishing event {} ({}) failed on attempt {}; now {}",
          event.id(),
          event.eventType(),
          failed.attempts(),
          failed.status(),
          e);
      return failed;
    }
  }
}

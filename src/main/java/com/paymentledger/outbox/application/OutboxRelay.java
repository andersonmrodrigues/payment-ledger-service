package com.paymentledger.outbox.application;

import com.paymentledger.outbox.domain.EventPublisher;
import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.outbox.domain.OutboxRepository;
import com.paymentledger.outbox.domain.RetryPolicy;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes due events one batch at a time. Each event's outcome is recorded on its own, so one
 * failing event neither blocks nor rolls back the others in the batch.
 */
@Service
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxRepository outbox;
  private final EventPublisher publisher;
  private final RetryPolicy retryPolicy;
  private final int batchSize;
  private final Clock clock;

  public OutboxRelay(
      OutboxRepository outbox, EventPublisher publisher, OutboxProperties properties, Clock clock) {
    this.outbox = outbox;
    this.publisher = publisher;
    this.retryPolicy = properties.retryPolicy();
    this.batchSize = properties.relay().batchSize();
    this.clock = clock;
  }

  /** Returns how many events were handled, published or not. */
  @Transactional
  public int publishDue() {
    List<OutboxEvent> due = outbox.claimDue(clock.instant(), batchSize);
    for (OutboxEvent event : due) {
      outbox.update(attempt(event));
    }
    return due.size();
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

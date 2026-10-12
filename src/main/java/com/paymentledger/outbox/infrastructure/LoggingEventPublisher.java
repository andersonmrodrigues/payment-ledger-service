package com.paymentledger.outbox.infrastructure;

import com.paymentledger.outbox.domain.EventPublisher;
import com.paymentledger.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stands in for a message broker: delivering an event means logging it. */
@Component
class LoggingEventPublisher implements EventPublisher {

  private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

  @Override
  public void publish(OutboxEvent event) {
    log.info(
        "Published {} {} for {} {}: {}",
        event.eventType(),
        event.id(),
        event.aggregateType(),
        event.aggregateId(),
        event.payload());
  }
}

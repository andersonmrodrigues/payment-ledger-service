package com.paymentledger.outbox.domain;

/** Delivers an event to the outside world; throws when delivery fails. */
public interface EventPublisher {

  void publish(OutboxEvent event);
}

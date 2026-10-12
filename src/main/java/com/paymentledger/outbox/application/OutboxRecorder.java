package com.paymentledger.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.outbox.domain.OutboxRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes events next to the state change that caused them. */
@Service
public class OutboxRecorder {

  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final Clock clock;

  public OutboxRecorder(OutboxRepository outbox, ObjectMapper json, Clock clock) {
    this.outbox = outbox;
    this.json = json;
    this.clock = clock;
  }

  /**
   * MANDATORY: an event recorded outside the business transaction could be published for a change
   * that later rolls back, or be lost for one that commits.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String aggregateType, UUID aggregateId, Object event) {
    String payload;
    try {
      payload = json.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize " + event.getClass(), e);
    }
    outbox.add(
        OutboxEvent.pending(
            aggregateType,
            aggregateId,
            event.getClass().getSimpleName(),
            payload,
            clock.instant()));
  }
}

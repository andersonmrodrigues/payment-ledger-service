package com.paymentledger.outbox.infrastructure;

import com.paymentledger.outbox.application.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Off on instances that should only serve requests, e.g. when relays run as separate workers. */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true")
class OutboxRelayScheduler {

  private final OutboxRelay relay;

  OutboxRelayScheduler(OutboxRelay relay) {
    this.relay = relay;
  }

  /** Drains every due event, one batch at a time, then waits for the next poll. */
  @Scheduled(fixedDelayString = "${outbox.relay.poll-interval}")
  void drain() {
    while (relay.publishDue() > 0) {
      // repeat until nothing is due; failed events are rescheduled into the future
    }
  }
}

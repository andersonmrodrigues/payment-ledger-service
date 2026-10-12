package com.paymentledger.outbox.infrastructure;

import com.paymentledger.outbox.application.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true")
class OutboxRelayScheduler {

  private final OutboxRelay relay;

  OutboxRelayScheduler(OutboxRelay relay) {
    this.relay = relay;
  }

  /** Drains every due event, one batch per transaction, then waits for the next poll. */
  @Scheduled(fixedDelayString = "${outbox.relay.poll-interval}")
  void drain() {
    while (relay.publishDue() > 0) {
      // repeat until nothing is due; failed events are rescheduled into the future
    }
  }
}

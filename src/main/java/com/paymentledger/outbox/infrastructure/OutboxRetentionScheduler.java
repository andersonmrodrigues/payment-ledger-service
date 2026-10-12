package com.paymentledger.outbox.infrastructure;

import com.paymentledger.outbox.application.OutboxRetention;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxRetentionScheduler {

  private final OutboxRetention retention;

  OutboxRetentionScheduler(OutboxRetention retention) {
    this.retention = retention;
  }

  /** Several instances may run this at once; the deletes do not conflict. */
  @Scheduled(cron = "${outbox.retention.cron}", zone = "UTC")
  void purge() {
    retention.purge();
  }
}

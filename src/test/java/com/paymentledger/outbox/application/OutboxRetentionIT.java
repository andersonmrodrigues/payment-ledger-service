package com.paymentledger.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRetentionIT extends IntegrationTest {

  @Autowired OutboxRecorder recorder;
  @Autowired OutboxRelay relay;
  @Autowired OutboxRetention retention;
  @Autowired TransactionTemplate tx;

  record Ping(String label) {}

  @Test
  void deletesOnlyPublishedEventsPastTheRetentionPeriod() {
    for (int i = 0; i < 5; i++) {
      record("old-published");
    }
    relay.publishDue();
    publisher.failAll(true);
    record("dead");
    for (int attempt = 0; attempt < 3; attempt++) {
      relay.publishDue();
      clock.advance(Duration.ofMinutes(1));
    }
    publisher.failAll(false);

    clock.advance(Duration.ofDays(29));
    record("recent-published");
    relay.publishDue();
    record("pending");
    clock.advance(Duration.ofDays(2));

    assertThat(retention.purge()).isEqualTo(5);
    assertThat(
            jdbc.sql("select payload->>'label' from outbox_events order by position")
                .query(String.class)
                .list())
        .containsExactly("dead", "recent-published", "pending");
  }

  private void record(String label) {
    tx.executeWithoutResult(status -> recorder.record("Test", UUID.randomUUID(), new Ping(label)));
  }
}

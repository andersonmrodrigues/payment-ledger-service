package com.paymentledger.outbox.application;

import static com.paymentledger.outbox.OutboxTestSupport.row;
import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import com.paymentledger.outbox.OutboxTestSupport.Row;
import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.outbox.domain.OutboxStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayIT extends IntegrationTest {

  @Autowired OutboxRecorder recorder;
  @Autowired OutboxRelay relay;
  @Autowired TransactionTemplate tx;

  record Ping(int n) {}

  @Test
  void publishesPendingEventsOnce() {
    List<UUID> ids = record(3);

    assertThat(relay.publishDue()).isEqualTo(3);
    assertThat(relay.publishDue()).isZero();

    assertThat(publisher.published()).extracting(OutboxEvent::id).containsExactlyElementsOf(ids);
    ids.forEach(id -> assertThat(row(jdbc, id).status()).isEqualTo(OutboxStatus.PUBLISHED));
  }

  @Test
  void oneFailingEventDoesNotBlockTheRestOfItsBatch() {
    List<UUID> ids = record(3);
    publisher.failFor(ids.get(1));

    relay.publishDue();

    assertThat(publisher.published())
        .extracting(OutboxEvent::id)
        .containsExactly(ids.get(0), ids.get(2));
    assertThat(row(jdbc, ids.get(1)))
        .isEqualTo(new Row(OutboxStatus.PENDING, 1, "broker unavailable"));
  }

  @Test
  void retriesWithBackoffThenGivesUp() {
    UUID id = record(1).getFirst();
    publisher.failAll(true);

    relay.publishDue(); // attempt 1 fails, next try in 1s
    assertThat(relay.publishDue()).as("not due before the backoff").isZero();
    clock.advance(Duration.ofSeconds(1));
    relay.publishDue(); // attempt 2 fails, next try in 2s
    clock.advance(Duration.ofSeconds(1));
    assertThat(relay.publishDue()).as("backoff doubled").isZero();
    clock.advance(Duration.ofSeconds(1));
    relay.publishDue(); // attempt 3 fails, max-attempts=3 in tests

    assertThat(row(jdbc, id).status()).isEqualTo(OutboxStatus.DEAD);
    assertThat(row(jdbc, id).attempts()).isEqualTo(3);
    clock.advance(Duration.ofDays(1));
    assertThat(relay.publishDue()).as("dead events wait for a replay").isZero();
  }

  @Test
  void recoversWhenTheBrokerComesBack() {
    UUID id = record(1).getFirst();
    publisher.failAll(true);
    relay.publishDue();

    publisher.failAll(false);
    clock.advance(Duration.ofSeconds(1));
    relay.publishDue();

    assertThat(row(jdbc, id).status()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(publisher.published()).extracting(OutboxEvent::id).containsExactly(id);
  }

  /** Relays on several nodes share one table; row locks with SKIP LOCKED keep their work apart. */
  @Test
  void parallelRelaysNeverPublishTheSameEventTwice() throws Exception {
    List<UUID> ids = record(300);
    int relays = 4;
    var start = new CountDownLatch(1);
    List<Future<?>> runs = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(relays)) {
      for (int i = 0; i < relays; i++) {
        runs.add(
            pool.submit(
                () -> {
                  start.await();
                  while (relay.publishDue() > 0) {
                    // drain
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> run : runs) {
        run.get();
      }
    }

    assertThat(publisher.published())
        .extracting(OutboxEvent::id)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(ids);
  }

  private List<UUID> record(int count) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int n = i;
      tx.executeWithoutResult(status -> recorder.record("Test", UUID.randomUUID(), new Ping(n)));
      // distinct creation times keep the publishing order deterministic
      clock.advance(Duration.ofMillis(1));
    }
    ids.addAll(
        jdbc.sql("select id from outbox_events order by created_at").query(UUID.class).list());
    return ids;
  }
}

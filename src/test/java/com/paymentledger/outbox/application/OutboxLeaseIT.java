package com.paymentledger.outbox.application;

import static com.paymentledger.outbox.OutboxTestSupport.row;
import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.outbox.domain.OutboxRepository;
import com.paymentledger.outbox.domain.OutboxStatus;
import com.paymentledger.outbox.domain.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxLeaseIT extends IntegrationTest {

  private static final Duration LEASE = Duration.ofSeconds(60);

  @Autowired OutboxRecorder recorder;
  @Autowired OutboxRelay relay;
  @Autowired OutboxRepository outbox;
  @Autowired TransactionTemplate tx;

  record Ping(int n) {}

  /** A slow broker must not keep rows locked: payments and replays would queue behind it. */
  @Test
  void holdsNoRowLockWhilePublishing() throws Exception {
    UUID id = recordOne();
    var publishing = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    publisher.beforePublish(
        event -> {
          publishing.countDown();
          awaitQuietly(release);
        });

    try (var pool = Executors.newSingleThreadExecutor()) {
      var run = pool.submit(relay::publishDue);
      assertThat(publishing.await(10, TimeUnit.SECONDS)).isTrue();

      Boolean lockedWhilePublishing =
          tx.execute(
              status ->
                  jdbc.sql("select id from outbox_events where id = ? for update nowait")
                      .param(id)
                      .query(UUID.class)
                      .optional()
                      .isPresent());

      release.countDown();
      assertThat(run.get(10, TimeUnit.SECONDS)).isEqualTo(1);
      assertThat(lockedWhilePublishing).isTrue();
    }
    assertThat(row(jdbc, id).status()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void eventsOfARelayThatDiedArePublishedOnceTheLeaseRunsOut() {
    UUID id = recordOne();
    Instant now = clock.instant();
    outbox.claimDue(now, now.plus(LEASE), UUID.randomUUID(), 10); // and then the relay died

    assertThat(relay.publishDue()).as("still leased").isZero();
    clock.advance(LEASE.plusSeconds(1));

    assertThat(relay.publishDue()).isEqualTo(1);
    assertThat(publisher.published()).extracting(OutboxEvent::id).containsExactly(id);
  }

  @Test
  void aRelayWhoseLeaseRanOutCannotOverwriteTheNewOutcome() {
    UUID id = recordOne();
    Instant now = clock.instant();
    UUID staleLease = UUID.randomUUID();
    OutboxEvent claimedBySlowRelay =
        outbox.claimDue(now, now.plus(LEASE), staleLease, 10).getFirst();

    clock.advance(LEASE.plusSeconds(1));
    relay.publishDue(); // another relay takes over and publishes

    var policy = new RetryPolicy(Duration.ofSeconds(1), Duration.ofMinutes(1), 3);
    boolean recorded =
        outbox.complete(
            claimedBySlowRelay.failed("timed out", clock.instant(), policy), staleLease);

    assertThat(recorded).isFalse();
    assertThat(row(jdbc, id).status()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void aClaimReturnsEventsInTheOrderTheyWereRecorded() {
    List<UUID> recorded = List.of(recordOne(), recordOne(), recordOne());
    Instant now = clock.instant();

    assertThat(outbox.claimDue(now, now.plus(LEASE), UUID.randomUUID(), 10))
        .extracting(OutboxEvent::id)
        .containsExactlyElementsOf(recorded);
  }

  private UUID recordOne() {
    UUID aggregate = UUID.randomUUID();
    tx.executeWithoutResult(status -> recorder.record("Test", aggregate, new Ping(1)));
    return jdbc.sql("select id from outbox_events where aggregate_id = ?")
        .param(aggregate)
        .query(UUID.class)
        .single();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

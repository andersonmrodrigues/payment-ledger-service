package com.paymentledger.outbox.application;

import static com.paymentledger.outbox.OutboxTestSupport.row;
import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.outbox.domain.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxReplayIT extends IntegrationTest {

  @Autowired OutboxRecorder recorder;
  @Autowired OutboxRelay relay;
  @Autowired TransactionTemplate tx;

  record Ping(int n) {}

  @Test
  void replaysADeadEventWithItsOriginalId() {
    UUID id = recordOne(1);
    publisher.failAll(true);
    for (int attempt = 0; attempt < 3; attempt++) {
      relay.publishDue();
      clock.advance(Duration.ofMinutes(1));
    }
    assertThat(row(jdbc, id).status()).isEqualTo(OutboxStatus.DEAD);

    publisher.failAll(false);
    var response = replay(Map.of("eventIds", List.of(id)));
    relay.publishDue();

    assertThat(response).isEqualTo(Map.of("requeued", 1));
    assertThat(row(jdbc, id).status()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(publisher.published()).extracting(OutboxEvent::id).containsExactly(id);
  }

  @Test
  void replaysPublishedEventsInATimeWindow() {
    UUID early = recordOne(1);
    clock.advance(Duration.ofHours(1));
    UUID inWindow = recordOne(2);
    clock.advance(Duration.ofHours(1));
    UUID late = recordOne(3);
    relay.publishDue();

    Instant from = clock.instant().minus(Duration.ofMinutes(90));
    Instant to = clock.instant().minus(Duration.ofMinutes(30));
    var response = replay(Map.of("from", from.toString(), "to", to.toString()));
    relay.publishDue();

    assertThat(response).isEqualTo(Map.of("requeued", 1));
    assertThat(publisher.published())
        .extracting(OutboxEvent::id)
        .containsExactly(early, inWindow, late, inWindow);
  }

  @Test
  void rejectsAnAmbiguousOrEmptyReplayRequest() {
    assertThat(badReplay(Map.of())).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            badReplay(
                Map.of("eventIds", List.of(UUID.randomUUID()), "from", "2026-01-01T00:00:00Z")))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badReplay(Map.of("from", "2026-02-01T00:00:00Z", "to", "2026-01-01T00:00:00Z")))
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private UUID recordOne(int n) {
    UUID aggregate = UUID.randomUUID();
    tx.executeWithoutResult(status -> recorder.record("Test", aggregate, new Ping(n)));
    return jdbc.sql("select id from outbox_events where aggregate_id = ?")
        .param(aggregate)
        .query(UUID.class)
        .single();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> replay(Map<String, ?> body) {
    return http.postForObject("/outbox/replay", body, Map.class);
  }

  private HttpStatus badReplay(Map<String, ?> body) {
    return HttpStatus.valueOf(
        http.postForEntity("/outbox/replay", body, ProblemDetail.class).getStatusCode().value());
  }
}

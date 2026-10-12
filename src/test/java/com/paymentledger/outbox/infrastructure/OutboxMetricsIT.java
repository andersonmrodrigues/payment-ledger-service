package com.paymentledger.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentledger.IntegrationTest;
import com.paymentledger.outbox.application.OutboxRecorder;
import com.paymentledger.outbox.application.OutboxRelay;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.transaction.support.TransactionTemplate;

// Spring Boot tests leave metric exporters off unless asked
@AutoConfigureObservability(tracing = false)
class OutboxMetricsIT extends IntegrationTest {

  @Autowired OutboxRecorder recorder;
  @Autowired OutboxRelay relay;
  @Autowired TransactionTemplate tx;
  @Autowired MeterRegistry metrics;

  record Ping(int n) {}

  @Test
  void reportsTheBacklogAndDeadEvents() {
    for (int n = 0; n < 2; n++) {
      int i = n;
      tx.executeWithoutResult(status -> recorder.record("Test", UUID.randomUUID(), new Ping(i)));
    }
    assertThat(gauge("PENDING")).isEqualTo(2);

    publisher.failAll(true);
    for (int attempt = 0; attempt < 3; attempt++) {
      relay.publishDue();
      clock.advance(Duration.ofMinutes(1));
    }

    assertThat(gauge("PENDING")).isZero();
    assertThat(gauge("DEAD")).isEqualTo(2);
  }

  @Test
  void exposesHealthProbesAndPrometheusMetrics() {
    assertThat(http.getForObject("/actuator/health/readiness", String.class)).contains("UP");
    assertThat(http.getForObject("/actuator/prometheus", String.class))
        .contains("outbox_events{status=\"DEAD\"}");
  }

  private double gauge(String status) {
    return metrics.get("outbox.events").tag("status", status).gauge().value();
  }
}

package com.paymentledger.outbox.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Exposes {@code outbox.events{status=PENDING|DEAD}}. A growing backlog means the relay or broker
 * is behind; any dead event needs someone to fix the cause and replay it, so alert on it.
 */
@Component
class OutboxMetrics implements MeterBinder {

  private final JdbcClient jdbc;

  OutboxMetrics(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (String status : new String[] {"PENDING", "DEAD"}) {
      Gauge.builder("outbox.events", () -> count(status))
          .tag("status", status)
          .description("Outbox events waiting to be published, or given up on")
          .register(registry);
    }
  }

  private long count(String status) {
    return jdbc.sql("select count(*) from outbox_events where status = ?")
        .param(status)
        .query(Long.class)
        .single();
  }
}

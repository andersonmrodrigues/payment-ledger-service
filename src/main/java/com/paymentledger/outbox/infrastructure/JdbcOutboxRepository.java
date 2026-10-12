package com.paymentledger.outbox.infrastructure;

import com.paymentledger.outbox.domain.OutboxEvent;
import com.paymentledger.outbox.domain.OutboxRepository;
import com.paymentledger.outbox.domain.OutboxStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboxRepository implements OutboxRepository {

  private final JdbcClient jdbc;

  JdbcOutboxRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(OutboxEvent event) {
    jdbc.sql(
            """
            insert into outbox_events (id, aggregate_type, aggregate_id, event_type, payload,
                                       status, attempts, next_attempt_at, created_at)
            values (?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?)
            """)
        .params(
            event.id(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            event.payload(),
            event.status().name(),
            event.attempts(),
            Timestamp.from(event.nextAttemptAt()),
            Timestamp.from(event.createdAt()))
        .update();
  }

  @Override
  public List<OutboxEvent> claimDue(Instant now, Instant leaseUntil, UUID lease, int limit) {
    // one statement, so the row locks last only as long as it does
    return jdbc.sql(
            """
            with due as (
                select id from outbox_events
                where status = 'PENDING' and next_attempt_at <= ?
                order by position
                limit ?
                for update skip locked
            ), claimed as (
                update outbox_events o
                set next_attempt_at = ?, lease_token = ?
                from due
                where o.id = due.id
                returning o.*
            )
            select * from claimed order by position
            """)
        .params(Timestamp.from(now), limit, Timestamp.from(leaseUntil), lease)
        .query(JdbcOutboxRepository::map)
        .list();
  }

  @Override
  public boolean complete(OutboxEvent outcome, UUID lease) {
    return jdbc.sql(
                """
                update outbox_events
                set status = ?, attempts = ?, next_attempt_at = ?, last_error = ?,
                    published_at = ?, lease_token = null
                where id = ? and lease_token = ?
                """)
            .params(
                outcome.status().name(),
                outcome.attempts(),
                Timestamp.from(outcome.nextAttemptAt()),
                outcome.lastError(),
                outcome.publishedAt() == null ? null : Timestamp.from(outcome.publishedAt()),
                outcome.id(),
                lease)
            .update()
        == 1;
  }

  @Override
  public int requeue(Collection<UUID> ids, Instant now) {
    return jdbc.sql(REQUEUE + " where id = any(?)")
        .params(Timestamp.from(now), ids.toArray(UUID[]::new))
        .update();
  }

  @Override
  public int requeueCreatedBetween(Instant from, Instant to, Instant now) {
    return jdbc.sql(REQUEUE + " where created_at >= ? and created_at < ?")
        .params(Timestamp.from(now), Timestamp.from(from), Timestamp.from(to))
        .update();
  }

  @Override
  public int deletePublishedBefore(Instant cutoff, int limit) {
    return jdbc.sql(
            """
            delete from outbox_events
            where id in (
                select id from outbox_events
                where status = 'PUBLISHED' and published_at < ?
                limit ?)
            """)
        .params(Timestamp.from(cutoff), limit)
        .update();
  }

  private static final String REQUEUE =
      """
      update outbox_events
      set status = 'PENDING', attempts = 0, next_attempt_at = ?, last_error = null,
          published_at = null, lease_token = null
      """;

  private static OutboxEvent map(ResultSet rs, int row) throws SQLException {
    Timestamp publishedAt = rs.getTimestamp("published_at");
    return new OutboxEvent(
        rs.getObject("id", UUID.class),
        rs.getString("aggregate_type"),
        rs.getObject("aggregate_id", UUID.class),
        rs.getString("event_type"),
        rs.getString("payload"),
        OutboxStatus.valueOf(rs.getString("status")),
        rs.getInt("attempts"),
        rs.getTimestamp("next_attempt_at").toInstant(),
        rs.getString("last_error"),
        rs.getTimestamp("created_at").toInstant(),
        publishedAt == null ? null : publishedAt.toInstant());
  }
}

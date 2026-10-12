package com.paymentledger.outbox;

import com.paymentledger.outbox.domain.OutboxStatus;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class OutboxTestSupport {

  private OutboxTestSupport() {}

  public record Row(OutboxStatus status, int attempts, String lastError) {}

  public static Row row(JdbcClient jdbc, UUID id) {
    return jdbc.sql("select status, attempts, last_error from outbox_events where id = ?")
        .param(id)
        .query(
            (rs, n) ->
                new Row(OutboxStatus.valueOf(rs.getString(1)), rs.getInt(2), rs.getString(3)))
        .single();
  }
}

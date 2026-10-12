package com.paymentledger.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Forgets idempotency keys past the retention period. A client retrying later than that with the
 * same key runs the operation again; for captures and refunds the unique PSP reference still turns
 * that into a 409 rather than a duplicate.
 */
@Component
public class IdempotencyRetention {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyRetention.class);

  private final JdbcClient jdbc;
  private final Duration period;
  private final int batchSize;
  private final Clock clock;

  public IdempotencyRetention(
      JdbcClient jdbc,
      @Value("${idempotency.retention.period}") Duration period,
      @Value("${idempotency.retention.batch-size}") int batchSize,
      Clock clock) {
    this.jdbc = jdbc;
    this.period = period;
    this.batchSize = batchSize;
    this.clock = clock;
  }

  /** Several instances may run this at once; the deletes do not conflict. */
  @Scheduled(cron = "${idempotency.retention.cron}", zone = "UTC")
  public void purge() {
    var cutoff = clock.instant().minus(period);
    int total = 0;
    int deleted;
    do {
      deleted =
          jdbc.sql(
                  """
                  delete from idempotency_keys
                  where (scope, key) in (
                      select scope, key from idempotency_keys where created_at < ? limit ?)
                  """)
              .params(Timestamp.from(cutoff), batchSize)
              .update();
      total += deleted;
    } while (deleted == batchSize);
    log.info("Deleted {} idempotency keys created before {}", total, cutoff);
  }
}

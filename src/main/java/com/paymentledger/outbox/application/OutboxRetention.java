package com.paymentledger.outbox.application;

import com.paymentledger.outbox.domain.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Deletes published events past the retention period. Dead events are kept until someone replays
 * them. A replay window cannot reach further back than the retention period.
 */
@Service
public class OutboxRetention {

  private static final Logger log = LoggerFactory.getLogger(OutboxRetention.class);

  private final OutboxRepository outbox;
  private final Duration period;
  private final int batchSize;
  private final Clock clock;

  public OutboxRetention(OutboxRepository outbox, OutboxProperties properties, Clock clock) {
    this.outbox = outbox;
    this.period = properties.retention().period();
    this.batchSize = properties.retention().batchSize();
    this.clock = clock;
  }

  /** Deletes in batches, each its own short statement, so a large backlog never locks for long. */
  public int purge() {
    var cutoff = clock.instant().minus(period);
    int total = 0;
    int deleted;
    do {
      deleted = outbox.deletePublishedBefore(cutoff, batchSize);
      total += deleted;
    } while (deleted == batchSize);
    log.info("Deleted {} outbox events published before {}", total, cutoff);
    return total;
  }
}

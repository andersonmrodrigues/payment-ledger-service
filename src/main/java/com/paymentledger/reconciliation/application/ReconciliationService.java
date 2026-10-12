package com.paymentledger.reconciliation.application;

import com.paymentledger.reconciliation.domain.BookedOperations;
import com.paymentledger.reconciliation.domain.OperationKey;
import com.paymentledger.reconciliation.domain.Reconciler;
import com.paymentledger.reconciliation.domain.ReconciliationNotFoundException;
import com.paymentledger.reconciliation.domain.ReconciliationPeriod;
import com.paymentledger.reconciliation.domain.ReconciliationReport;
import com.paymentledger.reconciliation.domain.ReconciliationRun;
import com.paymentledger.reconciliation.domain.ReconciliationRunRepository;
import com.paymentledger.reconciliation.domain.StatementLine;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

  private final BookedOperations operations;
  private final ReconciliationRunRepository runs;
  private final Clock clock;

  public ReconciliationService(
      BookedOperations operations, ReconciliationRunRepository runs, Clock clock) {
    this.operations = operations;
    this.runs = runs;
    this.clock = clock;
  }

  /**
   * Operations recorded in the period must appear in the statement. Lines may also settle
   * operations recorded before it, since PSPs settle a day or more after capture.
   */
  @Transactional
  public ReconciliationRun reconcile(ReconciliationPeriod period, List<StatementLine> statement) {
    List<OperationKey> referenced = statement.stream().map(StatementLine::key).distinct().toList();
    ReconciliationReport report =
        Reconciler.reconcile(
            statement,
            operations.recordedBetween(period.from(), period.to()),
            operations.withKeys(referenced));

    var run = new ReconciliationRun(UUID.randomUUID(), period, report, clock.instant());
    runs.add(run);
    return run;
  }

  @Transactional(readOnly = true)
  public ReconciliationRun get(UUID id) {
    return runs.findById(id).orElseThrow(() -> new ReconciliationNotFoundException(id));
  }
}

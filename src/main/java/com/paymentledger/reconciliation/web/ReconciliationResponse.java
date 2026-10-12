package com.paymentledger.reconciliation.web;

import com.paymentledger.reconciliation.domain.Discrepancy;
import com.paymentledger.reconciliation.domain.ReconciliationRun;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReconciliationResponse(
    UUID id,
    LocalDate periodStart,
    LocalDate periodEnd,
    int statementLines,
    int matched,
    List<Discrepancy> discrepancies,
    Instant createdAt) {

  static ReconciliationResponse from(ReconciliationRun run) {
    return new ReconciliationResponse(
        run.id(),
        run.period().start(),
        run.period().end(),
        run.report().statementLines(),
        run.report().matched(),
        run.report().discrepancies(),
        run.createdAt());
  }
}

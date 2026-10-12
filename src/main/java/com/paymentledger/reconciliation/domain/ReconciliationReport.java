package com.paymentledger.reconciliation.domain;

import java.util.List;

/**
 * @param matched statement lines that agree with the operation and its journal entry
 */
public record ReconciliationReport(
    int statementLines, int matched, List<Discrepancy> discrepancies) {

  public ReconciliationReport {
    discrepancies = List.copyOf(discrepancies);
  }
}

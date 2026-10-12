package com.paymentledger.reconciliation.domain;

import static com.paymentledger.reconciliation.domain.DiscrepancyType.AMOUNT_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.CURRENCY_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.DUPLICATE_IN_STATEMENT;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.LEDGER_MISMATCH;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.MISSING_IN_SERVICE;
import static com.paymentledger.reconciliation.domain.DiscrepancyType.MISSING_IN_STATEMENT;

import com.paymentledger.shared.domain.Money;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Three-way match: what the PSP settled, what this service recorded, and what the ledger booked.
 */
public final class Reconciler {

  private Reconciler() {}

  /**
   * @param inPeriod operations recorded in the statement's period; each should appear in it
   * @param referenced operations outside the period that statement lines point to
   */
  public static ReconciliationReport reconcile(
      List<StatementLine> statement,
      Collection<BookedOperation> inPeriod,
      Collection<BookedOperation> referenced) {
    Map<OperationKey, BookedOperation> known = new LinkedHashMap<>();
    referenced.forEach(op -> known.put(op.key(), op));
    inPeriod.forEach(op -> known.put(op.key(), op));

    List<Discrepancy> discrepancies = new ArrayList<>();
    known.values().forEach(op -> ledgerMismatch(op).ifPresent(discrepancies::add));

    Map<OperationKey, StatementLine> firstLines = new LinkedHashMap<>();
    for (StatementLine line : statement) {
      StatementLine first = firstLines.putIfAbsent(line.key(), line);
      if (first != null) {
        discrepancies.add(
            Discrepancy.of(
                DUPLICATE_IN_STATEMENT,
                line.key(),
                "line " + line.lineNumber() + " repeats line " + first.lineNumber()));
      }
    }

    int matched = 0;
    for (StatementLine line : firstLines.values()) {
      BookedOperation op = known.get(line.key());
      if (op == null) {
        discrepancies.add(
            Discrepancy.of(MISSING_IN_SERVICE, line.key(), "settled " + line.amount()));
        continue;
      }
      Optional<Discrepancy> amountProblem = compareAmounts(line, op);
      amountProblem.ifPresent(discrepancies::add);
      if (amountProblem.isEmpty() && ledgerMismatch(op).isEmpty()) {
        matched++;
      }
    }

    for (BookedOperation op : inPeriod) {
      if (!firstLines.containsKey(op.key())) {
        discrepancies.add(
            Discrepancy.of(MISSING_IN_STATEMENT, op.key(), "recorded " + op.amount()));
      }
    }
    return new ReconciliationReport(statement.size(), matched, discrepancies);
  }

  private static Optional<Discrepancy> compareAmounts(StatementLine line, BookedOperation op) {
    Money settled = line.amount();
    if (!settled.currency().equals(op.amount().currency())) {
      return Optional.of(
          Discrepancy.of(
              CURRENCY_MISMATCH, op.key(), "settled " + settled + ", recorded " + op.amount()));
    }
    if (!settled.equals(op.amount())) {
      return Optional.of(
          Discrepancy.of(
              AMOUNT_MISMATCH, op.key(), "settled " + settled + ", recorded " + op.amount()));
    }
    return Optional.empty();
  }

  private static Optional<Discrepancy> ledgerMismatch(BookedOperation op) {
    if (op.clearingAmount().isEmpty()) {
      return Optional.of(
          Discrepancy.of(LEDGER_MISMATCH, op.key(), "no movement on PSP clearing in the ledger"));
    }
    Money booked = op.clearingAmount().get();
    if (!booked.equals(op.amount())) {
      return Optional.of(
          Discrepancy.of(
              LEDGER_MISMATCH, op.key(), "recorded " + op.amount() + ", ledger " + booked));
    }
    return Optional.empty();
  }
}

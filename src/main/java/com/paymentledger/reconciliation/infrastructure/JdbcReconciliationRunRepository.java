package com.paymentledger.reconciliation.infrastructure;

import com.paymentledger.reconciliation.domain.Discrepancy;
import com.paymentledger.reconciliation.domain.DiscrepancyType;
import com.paymentledger.reconciliation.domain.OperationType;
import com.paymentledger.reconciliation.domain.ReconciliationPeriod;
import com.paymentledger.reconciliation.domain.ReconciliationReport;
import com.paymentledger.reconciliation.domain.ReconciliationRun;
import com.paymentledger.reconciliation.domain.ReconciliationRunRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReconciliationRunRepository implements ReconciliationRunRepository {

  private final JdbcClient jdbc;
  private final JdbcTemplate batch;

  JdbcReconciliationRunRepository(JdbcClient jdbc, JdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  @Override
  public void add(ReconciliationRun run) {
    ReconciliationReport report = run.report();
    jdbc.sql(
            """
            insert into reconciliation_runs
                (id, period_start, period_end, statement_lines, matched, created_at)
            values (?, ?, ?, ?, ?, ?)
            """)
        .params(
            run.id(),
            Date.valueOf(run.period().start()),
            Date.valueOf(run.period().end()),
            report.statementLines(),
            report.matched(),
            Timestamp.from(run.createdAt()))
        .update();
    // a bad statement can produce thousands of discrepancies
    batch.batchUpdate(
        """
        insert into reconciliation_discrepancies
            (run_id, type, psp_reference, operation_type, detail)
        values (?, ?, ?, ?, ?)
        """,
        report.discrepancies().stream()
            .map(
                d ->
                    new Object[] {
                      run.id(),
                      d.type().name(),
                      d.pspReference(),
                      d.operationType().name(),
                      d.detail()
                    })
            .toList());
  }

  @Override
  public Optional<ReconciliationRun> findById(UUID id) {
    List<Discrepancy> discrepancies =
        jdbc.sql(
                """
                select type, psp_reference, operation_type, detail
                from reconciliation_discrepancies where run_id = ? order by id
                """)
            .param(id)
            .query(
                (rs, n) ->
                    new Discrepancy(
                        DiscrepancyType.valueOf(rs.getString("type")),
                        rs.getString("psp_reference"),
                        OperationType.valueOf(rs.getString("operation_type")),
                        rs.getString("detail")))
            .list();
    return jdbc.sql("select * from reconciliation_runs where id = ?")
        .param(id)
        .query(
            (rs, n) ->
                new ReconciliationRun(
                    id,
                    new ReconciliationPeriod(
                        rs.getDate("period_start").toLocalDate(),
                        rs.getDate("period_end").toLocalDate()),
                    new ReconciliationReport(
                        rs.getInt("statement_lines"), rs.getInt("matched"), discrepancies),
                    rs.getTimestamp("created_at").toInstant()))
        .optional();
  }
}

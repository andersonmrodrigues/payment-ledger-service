package com.paymentledger.reconciliation.infrastructure;

import static com.paymentledger.payment.application.PaymentBookkeeping.CAPTURE;
import static com.paymentledger.payment.application.PaymentBookkeeping.PSP_CLEARING;
import static com.paymentledger.payment.application.PaymentBookkeeping.REFUND;

import com.paymentledger.reconciliation.domain.BookedOperation;
import com.paymentledger.reconciliation.domain.BookedOperations;
import com.paymentledger.reconciliation.domain.OperationKey;
import com.paymentledger.reconciliation.domain.OperationType;
import com.paymentledger.shared.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Captures and refunds, each with the amount its journal entry moved on PSP clearing: debited for a
 * capture, credited for a refund. A missing entry or clearing posting reads as no amount.
 */
@Repository
class JdbcBookedOperations implements BookedOperations {

  private static final String OPERATIONS =
      """
      with operations as (
          select p.psp_reference, 'CAPTURE' as type, p.amount_minor, p.currency,
                 p.captured_at as recorded_at, :capture as reference_type,
                 p.id::text as reference_id, 1 as clearing_sign
          from payments p
          union all
          select r.psp_reference, 'REFUND', r.amount_minor, p.currency,
                 r.created_at, :refund, r.id::text, -1
          from refunds r
          join payments p on p.id = r.payment_id
      )
      select o.psp_reference, o.type, o.amount_minor, o.currency,
             (select o.clearing_sign
                     * sum(case po.direction when 'DEBIT' then po.amount_minor
                                             else -po.amount_minor end)
              from journal_entries je
              join postings po on po.entry_id = je.id
              join accounts a on a.id = po.account_id
              where je.reference_type = o.reference_type
                and je.reference_id = o.reference_id
                and a.code = :clearing) as clearing_minor
      from operations o
      """;

  private static final String ORDER = " order by o.recorded_at, o.psp_reference, o.type";

  private final JdbcClient jdbc;

  JdbcBookedOperations(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<BookedOperation> recordedBetween(Instant from, Instant to) {
    return jdbc.sql(OPERATIONS + " where o.recorded_at >= :from and o.recorded_at < :to" + ORDER)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .params(ledgerReferences())
        .query(JdbcBookedOperations::map)
        .list();
  }

  @Override
  public List<BookedOperation> withKeys(Collection<OperationKey> keys) {
    if (keys.isEmpty()) {
      return List.of();
    }
    return jdbc
        .sql(OPERATIONS + " where o.psp_reference = any(:references)" + ORDER)
        .param(
            "references",
            keys.stream().map(OperationKey::pspReference).distinct().toArray(String[]::new))
        .params(ledgerReferences())
        .query(JdbcBookedOperations::map)
        .stream()
        .filter(op -> keys.contains(op.key()))
        .toList();
  }

  private static Map<String, Object> ledgerReferences() {
    return Map.of("capture", CAPTURE, "refund", REFUND, "clearing", PSP_CLEARING);
  }

  private static BookedOperation map(ResultSet rs, int row) throws SQLException {
    Currency currency = Currency.getInstance(rs.getString("currency"));
    long clearing = rs.getLong("clearing_minor");
    Optional<Money> clearingAmount =
        rs.wasNull() ? Optional.empty() : Optional.of(new Money(clearing, currency));
    return new BookedOperation(
        new OperationKey(
            rs.getString("psp_reference"), OperationType.valueOf(rs.getString("type"))),
        new Money(rs.getLong("amount_minor"), currency),
        clearingAmount);
  }
}

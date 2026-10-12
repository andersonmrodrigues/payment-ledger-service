package com.paymentledger.payment.infrastructure;

import com.paymentledger.payment.domain.DuplicatePspReferenceException;
import com.paymentledger.payment.domain.Payment;
import com.paymentledger.payment.domain.PaymentRepository;
import com.paymentledger.payment.domain.Refund;
import com.paymentledger.shared.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPaymentRepository implements PaymentRepository {

  private final JdbcClient jdbc;

  JdbcPaymentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(Payment payment) {
    try {
      jdbc.sql(
              """
              insert into payments
                (id, merchant_id, amount_minor, fee_minor, currency, psp_reference, captured_at)
              values (?, ?, ?, ?, ?, ?, ?)
              """)
          .params(
              payment.id(),
              payment.merchantId(),
              payment.amount().minorUnits(),
              payment.fee().minorUnits(),
              payment.amount().currency().getCurrencyCode(),
              payment.pspReference(),
              Timestamp.from(payment.capturedAt()))
          .update();
    } catch (DuplicateKeyException e) {
      throw new DuplicatePspReferenceException(payment.pspReference());
    }
  }

  @Override
  public void addRefund(Refund refund) {
    try {
      jdbc.sql(
              """
              insert into refunds
                (id, payment_id, amount_minor, fee_returned_minor, psp_reference, created_at)
              values (?, ?, ?, ?, ?, ?)
              """)
          .params(
              refund.id(),
              refund.paymentId(),
              refund.amount().minorUnits(),
              refund.feeReturned().minorUnits(),
              refund.pspReference(),
              Timestamp.from(refund.createdAt()))
          .update();
    } catch (DuplicateKeyException e) {
      throw new DuplicatePspReferenceException(refund.pspReference());
    }
  }

  @Override
  public Optional<Payment> findById(UUID id) {
    return find("select * from payments where id = ?", id);
  }

  @Override
  public Optional<Payment> findByIdForUpdate(UUID id) {
    return find("select * from payments where id = ? for update", id);
  }

  private Optional<Payment> find(String sql, UUID id) {
    return jdbc.sql(sql).param(id).query((rs, row) -> mapPayment(rs)).optional();
  }

  private Payment mapPayment(ResultSet rs) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    Currency currency = Currency.getInstance(rs.getString("currency"));
    return new Payment(
        id,
        rs.getString("merchant_id"),
        new Money(rs.getLong("amount_minor"), currency),
        new Money(rs.getLong("fee_minor"), currency),
        rs.getString("psp_reference"),
        rs.getTimestamp("captured_at").toInstant(),
        refundsOf(id, currency));
  }

  private List<Refund> refundsOf(UUID paymentId, Currency currency) {
    return jdbc.sql("select * from refunds where payment_id = ? order by created_at, id")
        .param(paymentId)
        .query(
            (rs, row) ->
                new Refund(
                    rs.getObject("id", UUID.class),
                    paymentId,
                    new Money(rs.getLong("amount_minor"), currency),
                    new Money(rs.getLong("fee_returned_minor"), currency),
                    rs.getString("psp_reference"),
                    rs.getTimestamp("created_at").toInstant()))
        .list();
  }
}

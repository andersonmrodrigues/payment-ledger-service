package com.paymentledger.payment.application;

import com.paymentledger.outbox.application.OutboxRecorder;
import com.paymentledger.payment.application.PaymentEvents.PaymentCaptured;
import com.paymentledger.payment.application.PaymentEvents.PaymentRefunded;
import com.paymentledger.payment.domain.FeePolicy;
import com.paymentledger.payment.domain.Payment;
import com.paymentledger.payment.domain.PaymentNotFoundException;
import com.paymentledger.payment.domain.PaymentRepository;
import com.paymentledger.payment.domain.Refund;
import com.paymentledger.shared.domain.Money;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

  private final PaymentRepository payments;
  private final PaymentBookkeeping bookkeeping;
  private final OutboxRecorder outbox;
  private final FeePolicy feePolicy;
  private final Clock clock;

  public PaymentService(
      PaymentRepository payments,
      PaymentBookkeeping bookkeeping,
      OutboxRecorder outbox,
      @Value("${payment.fee-basis-points}") int feeBasisPoints,
      Clock clock) {
    this.payments = payments;
    this.bookkeeping = bookkeeping;
    this.outbox = outbox;
    this.feePolicy = new FeePolicy(feeBasisPoints);
    this.clock = clock;
  }

  @Transactional
  public Payment capture(String clientId, String merchantId, Money amount, String pspReference) {
    Payment payment =
        Payment.capture(clientId, merchantId, amount, feePolicy, pspReference, clock.instant());
    payments.add(payment);
    bookkeeping.bookCapture(payment);
    outbox.record(PaymentEvents.AGGREGATE, payment.id(), PaymentCaptured.of(payment));
    return payment;
  }

  /** Refunds in the payment's own currency. Another client's payment is reported as not found. */
  @Transactional
  public Refund refund(String clientId, UUID paymentId, long amountMinor, String pspReference) {
    Payment payment =
        payments
            .findByIdForUpdate(paymentId)
            .filter(p -> p.belongsTo(clientId))
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    Money amount = new Money(amountMinor, payment.amount().currency());
    Refund refund = payment.refund(amount, pspReference, clock.instant());
    payments.addRefund(refund);
    bookkeeping.bookRefund(payment, refund);
    outbox.record(PaymentEvents.AGGREGATE, payment.id(), PaymentRefunded.of(payment, refund));
    return refund;
  }

  @Transactional(readOnly = true)
  public Payment get(String clientId, UUID paymentId) {
    return payments
        .findById(paymentId)
        .filter(p -> p.belongsTo(clientId))
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }
}

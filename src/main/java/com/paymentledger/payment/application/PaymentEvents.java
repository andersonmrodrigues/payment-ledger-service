package com.paymentledger.payment.application;

import com.paymentledger.payment.domain.Payment;
import com.paymentledger.payment.domain.Refund;
import java.time.Instant;
import java.util.UUID;

/** The events other systems receive about payments, through the outbox. */
public final class PaymentEvents {

  public static final String AGGREGATE = "Payment";

  private PaymentEvents() {}

  public record PaymentCaptured(
      UUID paymentId,
      String merchantId,
      long amountMinor,
      long feeMinor,
      String currency,
      String pspReference,
      Instant capturedAt) {

    static PaymentCaptured of(Payment payment) {
      return new PaymentCaptured(
          payment.id(),
          payment.merchantId(),
          payment.amount().minorUnits(),
          payment.fee().minorUnits(),
          payment.amount().currency().getCurrencyCode(),
          payment.pspReference(),
          payment.capturedAt());
    }
  }

  public record PaymentRefunded(
      UUID refundId,
      UUID paymentId,
      String merchantId,
      long amountMinor,
      long feeReturnedMinor,
      String currency,
      String pspReference,
      Instant refundedAt) {

    static PaymentRefunded of(Payment payment, Refund refund) {
      return new PaymentRefunded(
          refund.id(),
          payment.id(),
          payment.merchantId(),
          refund.amount().minorUnits(),
          refund.feeReturned().minorUnits(),
          refund.amount().currency().getCurrencyCode(),
          refund.pspReference(),
          refund.createdAt());
    }
  }
}

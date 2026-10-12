package com.paymentledger.payment.web;

import com.paymentledger.payment.domain.Payment;
import com.paymentledger.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    String merchantId,
    long amountMinor,
    long feeMinor,
    String currency,
    String pspReference,
    PaymentStatus status,
    long refundedMinor,
    Instant capturedAt,
    List<RefundResponse> refunds) {

  static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.id(),
        payment.merchantId(),
        payment.amount().minorUnits(),
        payment.fee().minorUnits(),
        payment.amount().currency().getCurrencyCode(),
        payment.pspReference(),
        payment.status(),
        payment.refundedAmount().minorUnits(),
        payment.capturedAt(),
        payment.refunds().stream().map(RefundResponse::from).toList());
  }
}

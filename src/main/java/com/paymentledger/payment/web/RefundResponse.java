package com.paymentledger.payment.web;

import com.paymentledger.payment.domain.Refund;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
    UUID id,
    UUID paymentId,
    long amountMinor,
    long feeReturnedMinor,
    String pspReference,
    Instant createdAt) {

  static RefundResponse from(Refund refund) {
    return new RefundResponse(
        refund.id(),
        refund.paymentId(),
        refund.amount().minorUnits(),
        refund.feeReturned().minorUnits(),
        refund.pspReference(),
        refund.createdAt());
  }
}

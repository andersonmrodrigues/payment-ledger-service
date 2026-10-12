package com.paymentledger.payment.domain;

import com.paymentledger.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Money returned to the payer. The merchant gives back {@code amount - feeReturned} and the fee
 * revenue gives back {@code feeReturned}.
 */
public record Refund(
    UUID id,
    UUID paymentId,
    Money amount,
    Money feeReturned,
    String pspReference,
    Instant createdAt) {}

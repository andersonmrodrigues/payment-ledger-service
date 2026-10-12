package com.paymentledger.payment.domain;

import com.paymentledger.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A captured payment and its refunds. Refunds never exceed the captured amount, and the fee is
 * returned pro rata so that a full refund returns exactly the fee that was kept. A payment belongs
 * to the API client that captured it.
 */
public final class Payment {

  private final UUID id;
  private final String clientId;
  private final String merchantId;
  private final Money amount;
  private final Money fee;
  private final String pspReference;
  private final Instant capturedAt;
  private final List<Refund> refunds;

  public Payment(
      UUID id,
      String clientId,
      String merchantId,
      Money amount,
      Money fee,
      String pspReference,
      Instant capturedAt,
      List<Refund> refunds) {
    this.id = Objects.requireNonNull(id, "id");
    this.clientId = Objects.requireNonNull(clientId, "clientId");
    this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
    this.amount = Objects.requireNonNull(amount, "amount");
    this.fee = Objects.requireNonNull(fee, "fee");
    this.pspReference = Objects.requireNonNull(pspReference, "pspReference");
    this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
    this.refunds = new ArrayList<>(refunds);
  }

  public static Payment capture(
      String clientId,
      String merchantId,
      Money amount,
      FeePolicy feePolicy,
      String pspReference,
      Instant now) {
    if (!amount.isPositive()) {
      throw new PaymentRuleViolationException("Captured amount must be positive");
    }
    return new Payment(
        UUID.randomUUID(),
        clientId,
        merchantId,
        amount,
        feePolicy.feeFor(amount),
        pspReference,
        now,
        List.of());
  }

  public Refund refund(Money refundAmount, String refundPspReference, Instant now) {
    if (!refundAmount.isPositive()) {
      throw new PaymentRuleViolationException("Refund amount must be positive");
    }
    if (!refundAmount.currency().equals(amount.currency())) {
      throw new PaymentRuleViolationException(
          "Refund currency " + refundAmount.currency() + " differs from " + amount.currency());
    }
    Money remaining = amount.minus(refundedAmount());
    if (refundAmount.isGreaterThan(remaining)) {
      throw new PaymentRuleViolationException(
          "Refund of " + refundAmount + " exceeds the refundable " + remaining);
    }

    Refund refund =
        new Refund(
            UUID.randomUUID(),
            id,
            refundAmount,
            feeReturnedFor(refundAmount, remaining),
            refundPspReference,
            now);
    refunds.add(refund);
    return refund;
  }

  /**
   * The pro rata share of the fee, kept within bounds so rounding can never return more fee than
   * was kept, nor leave more fee than amount still to refund (which would overdraw the merchant).
   */
  private Money feeReturnedFor(Money refundAmount, Money remainingBefore) {
    long feeRemaining = fee.minorUnits() - feeReturned().minorUnits();
    long amountRemainingAfter = remainingBefore.minorUnits() - refundAmount.minorUnits();
    long proRata =
        BigDecimal.valueOf(fee.minorUnits())
            .multiply(BigDecimal.valueOf(refundAmount.minorUnits()))
            .divide(BigDecimal.valueOf(amount.minorUnits()), 0, RoundingMode.HALF_EVEN)
            .longValueExact();
    long lower = Math.max(0, feeRemaining - amountRemainingAfter);
    long upper = Math.min(feeRemaining, refundAmount.minorUnits());
    return new Money(Math.clamp(proRata, lower, upper), amount.currency());
  }

  public Money refundedAmount() {
    return refunds.stream().map(Refund::amount).reduce(Money.zero(amount.currency()), Money::plus);
  }

  public Money feeReturned() {
    return refunds.stream()
        .map(Refund::feeReturned)
        .reduce(Money.zero(amount.currency()), Money::plus);
  }

  public PaymentStatus status() {
    Money refunded = refundedAmount();
    if (refunded.isZero()) {
      return PaymentStatus.CAPTURED;
    }
    return refunded.equals(amount) ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
  }

  public UUID id() {
    return id;
  }

  public String clientId() {
    return clientId;
  }

  public boolean belongsTo(String client) {
    return clientId.equals(client);
  }

  public String merchantId() {
    return merchantId;
  }

  public Money amount() {
    return amount;
  }

  public Money fee() {
    return fee;
  }

  public String pspReference() {
    return pspReference;
  }

  public Instant capturedAt() {
    return capturedAt;
  }

  public List<Refund> refunds() {
    return List.copyOf(refunds);
  }
}

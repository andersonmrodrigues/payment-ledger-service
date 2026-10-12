package com.paymentledger.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
  private static final FeePolicy FEE = new FeePolicy(290);

  @Test
  void captureKeepsTheFee() {
    Payment payment = capture(10_000);

    assertThat(payment.fee()).isEqualTo(Money.of(290, "BRL"));
    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
  }

  @Test
  void rejectsANonPositiveCapture() {
    assertThatThrownBy(() -> capture(0)).isInstanceOf(PaymentRuleViolationException.class);
  }

  @Test
  void partialThenFullRefundReturnsTheWholeFee() {
    Payment payment = capture(10_000);

    Refund first = payment.refund(Money.of(4_000, "BRL"), "r1", NOW);
    assertThat(first.feeReturned()).isEqualTo(Money.of(116, "BRL"));
    assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

    Refund second = payment.refund(Money.of(6_000, "BRL"), "r2", NOW);
    assertThat(second.feeReturned()).isEqualTo(Money.of(174, "BRL"));
    assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(payment.feeReturned()).isEqualTo(payment.fee());
  }

  @Test
  void rejectsARefundAboveTheRefundableAmount() {
    Payment payment = capture(10_000);
    payment.refund(Money.of(7_000, "BRL"), "r1", NOW);

    assertThatThrownBy(() -> payment.refund(Money.of(3_001, "BRL"), "r2", NOW))
        .isInstanceOf(PaymentRuleViolationException.class)
        .hasMessageContaining("exceeds the refundable 3000 BRL");
    assertThat(payment.refundedAmount()).isEqualTo(Money.of(7_000, "BRL"));
  }

  @Test
  void rejectsARefundOfAFullyRefundedPayment() {
    Payment payment = capture(10_000);
    payment.refund(Money.of(10_000, "BRL"), "r1", NOW);

    assertThatThrownBy(() -> payment.refund(Money.of(1, "BRL"), "r2", NOW))
        .isInstanceOf(PaymentRuleViolationException.class);
  }

  @Test
  void rejectsARefundInAnotherCurrencyOrNotPositive() {
    Payment payment = capture(10_000);

    assertThatThrownBy(() -> payment.refund(Money.of(100, "USD"), "r1", NOW))
        .isInstanceOf(PaymentRuleViolationException.class);
    assertThatThrownBy(() -> payment.refund(Money.of(0, "BRL"), "r1", NOW))
        .isInstanceOf(PaymentRuleViolationException.class);
  }

  @Test
  void roundedDownFeeSharesCatchUpBeforeTheMerchantIsOverdrawn() {
    // 50% fee on 4: each 1-unit refund's share is 0.5, which rounds down to 0.
    Payment payment = Payment.capture("m1", Money.of(4, "BRL"), new FeePolicy(5_000), "p", NOW);

    List<Long> returned = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      returned.add(payment.refund(Money.of(1, "BRL"), "r" + i, NOW).feeReturned().minorUnits());
    }

    // Without the lower bound the third refund would take 1 from a merchant holding 0.
    assertThat(returned).containsExactly(0L, 0L, 1L, 1L);
    assertThat(payment.feeReturned()).isEqualTo(Money.of(2, "BRL"));
  }

  /**
   * Rounding the pro rata fee on many small refunds must never return more fee than was kept, nor
   * take more from the merchant than they received, and a full refund returns exactly the fee.
   */
  @Property
  void feeReturnIsExactAndNeverOverdrawsTheMerchant(@ForAll("refundPlans") RefundPlan plan) {
    Payment payment = Payment.capture("m1", Money.of(plan.amount(), "BRL"), plan.fee(), "cap", NOW);

    for (long part : plan.parts()) {
      Refund refund = payment.refund(Money.of(part, "BRL"), "r" + part, NOW);
      assertThat(refund.feeReturned().minorUnits()).isBetween(0L, part);
      long merchantBalance =
          payment.amount().minus(payment.fee()).minorUnits()
              - payment.refundedAmount().minus(payment.feeReturned()).minorUnits();
      assertThat(merchantBalance).as("merchant payable").isNotNegative();
    }
    assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(payment.feeReturned()).isEqualTo(payment.fee());
  }

  record RefundPlan(long amount, FeePolicy fee, List<Long> parts) {}

  @Provide
  Arbitrary<RefundPlan> refundPlans() {
    // Rounding drift accumulates on small payments split into many tiny refunds, with a fee just
    // under half the amount (each share rounds down), so those cases are drawn often.
    Arbitrary<Long> amounts =
        Arbitraries.oneOf(
            Arbitraries.longs().between(1, 50), Arbitraries.longs().between(1, 10_000));
    Arbitrary<FeePolicy> fees =
        Arbitraries.oneOf(
                Arbitraries.integers().between(3_000, 5_000),
                Arbitraries.integers().between(0, 10_000))
            .map(FeePolicy::new);
    return Combinators.combine(amounts, fees)
        .flatAs(
            (amount, fee) ->
                Arbitraries.oneOf(
                        Arbitraries.longs().between(1, 3), Arbitraries.longs().between(1, amount))
                    .list()
                    .ofMaxSize(60)
                    .map(sizes -> new RefundPlan(amount, fee, split(amount, sizes))));
  }

  /** Turns random sizes into parts that sum exactly to amount. */
  private static List<Long> split(long amount, List<Long> sizes) {
    var parts = new ArrayList<Long>();
    long left = amount;
    for (long size : sizes) {
      if (left == 0) {
        break;
      }
      long part = Math.min(size, left);
      parts.add(part);
      left -= part;
    }
    if (left > 0) {
      parts.add(left);
    }
    return parts;
  }

  private static Payment capture(long amount) {
    return Payment.capture("m1", Money.of(amount, "BRL"), FEE, "psp-1", NOW);
  }
}

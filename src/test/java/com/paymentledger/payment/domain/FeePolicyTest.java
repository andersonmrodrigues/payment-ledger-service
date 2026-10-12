package com.paymentledger.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.shared.domain.Money;
import org.junit.jupiter.api.Test;

class FeePolicyTest {

  @Test
  void chargesBasisPointsOfTheAmount() {
    assertThat(new FeePolicy(290).feeFor(Money.of(10_000, "BRL"))).isEqualTo(Money.of(290, "BRL"));
  }

  @Test
  void roundsHalfToEven() {
    // 50 bps of 100 = 0.5 -> 0 ; of 300 = 1.5 -> 2
    assertThat(new FeePolicy(50).feeFor(Money.of(100, "BRL"))).isEqualTo(Money.of(0, "BRL"));
    assertThat(new FeePolicy(50).feeFor(Money.of(300, "BRL"))).isEqualTo(Money.of(2, "BRL"));
  }

  @Test
  void rejectsAFeeOutsideZeroToOneHundredPercent() {
    assertThatThrownBy(() -> new FeePolicy(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FeePolicy(10_001)).isInstanceOf(IllegalArgumentException.class);
  }
}

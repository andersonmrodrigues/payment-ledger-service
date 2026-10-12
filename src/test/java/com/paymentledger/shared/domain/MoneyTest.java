package com.paymentledger.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void addsAndSubtractsInTheSameCurrency() {
    assertThat(Money.of(1000, "BRL").plus(Money.of(250, "BRL"))).isEqualTo(Money.of(1250, "BRL"));
    assertThat(Money.of(1000, "BRL").minus(Money.of(250, "BRL"))).isEqualTo(Money.of(750, "BRL"));
  }

  @Test
  void rejectsArithmeticAcrossCurrencies() {
    assertThatThrownBy(() -> Money.of(1000, "BRL").plus(Money.of(1, "USD")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Currency mismatch");
  }

  @Test
  void failsInsteadOfOverflowing() {
    assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "BRL").plus(Money.of(1, "BRL")))
        .isInstanceOf(ArithmeticException.class);
  }
}

package com.paymentledger.payment.domain;

import com.paymentledger.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** The processing fee kept from each capture, in basis points of the captured amount. */
public record FeePolicy(int basisPoints) {

  private static final BigDecimal BASIS_POINTS_PER_UNIT = BigDecimal.valueOf(10_000);

  public FeePolicy {
    if (basisPoints < 0 || basisPoints > 10_000) {
      throw new IllegalArgumentException("Fee must be between 0 and 10000 basis points");
    }
  }

  public Money feeFor(Money amount) {
    long fee =
        BigDecimal.valueOf(amount.minorUnits())
            .multiply(BigDecimal.valueOf(basisPoints))
            .divide(BASIS_POINTS_PER_UNIT, 0, RoundingMode.HALF_EVEN)
            .longValueExact();
    return new Money(fee, amount.currency());
  }
}

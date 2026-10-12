package com.paymentledger.shared.domain;

import java.util.Currency;
import java.util.Objects;

/** An amount in the currency's minor unit (cents for BRL), so arithmetic is exact. */
public record Money(long minorUnits, Currency currency) {

  public Money {
    Objects.requireNonNull(currency, "currency");
  }

  public static Money of(long minorUnits, String currencyCode) {
    return new Money(minorUnits, Currency.getInstance(currencyCode));
  }

  public static Money zero(Currency currency) {
    return new Money(0, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
  }

  public Money negate() {
    return new Money(Math.negateExact(minorUnits), currency);
  }

  public boolean isPositive() {
    return minorUnits > 0;
  }

  public boolean isZero() {
    return minorUnits == 0;
  }

  public boolean isGreaterThan(Money other) {
    requireSameCurrency(other);
    return minorUnits > other.minorUnits;
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Currency mismatch: " + currency + " and " + other.currency);
    }
  }

  @Override
  public String toString() {
    return minorUnits + " " + currency;
  }
}

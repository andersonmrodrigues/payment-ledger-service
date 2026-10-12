package com.paymentledger.ledger.domain;

public enum AccountType {
  ASSET(Direction.DEBIT),
  LIABILITY(Direction.CREDIT),
  REVENUE(Direction.CREDIT);

  private final Direction normalSide;

  AccountType(Direction normalSide) {
    this.normalSide = normalSide;
  }

  /** The side on which a posting increases the balance of an account of this type. */
  public Direction normalSide() {
    return normalSide;
  }
}

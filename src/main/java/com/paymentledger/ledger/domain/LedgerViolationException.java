package com.paymentledger.ledger.domain;

/** A ledger invariant would be broken; the operation that triggered it must not commit. */
public class LedgerViolationException extends RuntimeException {

  public LedgerViolationException(String message) {
    super(message);
  }
}

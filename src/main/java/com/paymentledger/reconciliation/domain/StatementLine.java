package com.paymentledger.reconciliation.domain;

import com.paymentledger.shared.domain.Money;
import java.time.LocalDate;

/** One settled operation as the PSP reports it. */
public record StatementLine(
    int lineNumber, String pspReference, OperationType type, Money amount, LocalDate settledOn) {

  public OperationKey key() {
    return new OperationKey(pspReference, type);
  }
}

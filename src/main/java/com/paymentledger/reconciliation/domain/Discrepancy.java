package com.paymentledger.reconciliation.domain;

public record Discrepancy(
    DiscrepancyType type, String pspReference, OperationType operationType, String detail) {

  static Discrepancy of(DiscrepancyType type, OperationKey key, String detail) {
    return new Discrepancy(type, key.pspReference(), key.type(), detail);
  }
}

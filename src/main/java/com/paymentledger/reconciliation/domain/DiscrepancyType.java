package com.paymentledger.reconciliation.domain;

public enum DiscrepancyType {
  /** The PSP settled an operation this service has no record of. */
  MISSING_IN_SERVICE,
  /** This service recorded an operation in the period that the PSP did not settle. */
  MISSING_IN_STATEMENT,
  AMOUNT_MISMATCH,
  CURRENCY_MISMATCH,
  /** The operation's journal entry is missing or moved a different amount. */
  LEDGER_MISMATCH,
  DUPLICATE_IN_STATEMENT
}

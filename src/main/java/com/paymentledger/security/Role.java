package com.paymentledger.security;

public enum Role {
  /** Captures, refunds and reads its own payments. */
  PAYMENTS,
  /** Reconciliation and outbox replay. */
  BACK_OFFICE
}

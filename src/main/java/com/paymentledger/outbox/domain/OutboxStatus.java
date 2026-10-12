package com.paymentledger.outbox.domain;

public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  /** Out of retries; waits for an operator to replay it. */
  DEAD
}

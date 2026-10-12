package com.paymentledger.idempotency;

import com.paymentledger.shared.domain.RuleViolationException;

public class IdempotencyKeyReusedException extends RuleViolationException {

  public IdempotencyKeyReusedException(String key) {
    super("Idempotency key " + key + " was already used with a different request");
  }
}

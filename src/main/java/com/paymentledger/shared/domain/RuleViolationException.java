package com.paymentledger.shared.domain;

/** The request is well formed but breaks a business rule. */
public abstract class RuleViolationException extends RuntimeException {

  protected RuleViolationException(String message) {
    super(message);
  }
}

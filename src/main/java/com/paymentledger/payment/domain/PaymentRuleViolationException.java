package com.paymentledger.payment.domain;

import com.paymentledger.shared.domain.RuleViolationException;

/** The request is well formed but breaks a payment rule, such as refunding too much. */
public class PaymentRuleViolationException extends RuleViolationException {

  public PaymentRuleViolationException(String message) {
    super(message);
  }
}

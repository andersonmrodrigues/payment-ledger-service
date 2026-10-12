package com.paymentledger.payment.domain;

/** The request is well formed but breaks a payment rule, such as refunding too much. */
public class PaymentRuleViolationException extends RuntimeException {

  public PaymentRuleViolationException(String message) {
    super(message);
  }
}

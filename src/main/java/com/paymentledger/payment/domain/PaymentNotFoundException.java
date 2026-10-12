package com.paymentledger.payment.domain;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(UUID id) {
    super("Payment " + id + " not found");
  }
}

package com.paymentledger.payment.domain;

import com.paymentledger.shared.domain.NotFoundException;
import java.util.UUID;

public class PaymentNotFoundException extends NotFoundException {

  public PaymentNotFoundException(UUID id) {
    super("Payment " + id + " not found");
  }
}

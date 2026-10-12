package com.paymentledger.payment.domain;

import com.paymentledger.shared.domain.ConflictException;

public class DuplicatePspReferenceException extends ConflictException {

  public DuplicatePspReferenceException(String pspReference) {
    super("PSP reference " + pspReference + " is already recorded");
  }
}

package com.paymentledger.payment.domain;

public class DuplicatePspReferenceException extends RuntimeException {

  public DuplicatePspReferenceException(String pspReference) {
    super("PSP reference " + pspReference + " is already recorded");
  }
}

package com.paymentledger.shared.domain;

/** A requested resource does not exist. */
public abstract class NotFoundException extends RuntimeException {

  protected NotFoundException(String message) {
    super(message);
  }
}

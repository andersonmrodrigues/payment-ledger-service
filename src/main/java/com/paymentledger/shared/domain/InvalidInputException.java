package com.paymentledger.shared.domain;

/** Input from outside the service cannot be understood. */
public abstract class InvalidInputException extends RuntimeException {

  protected InvalidInputException(String message) {
    super(message);
  }
}

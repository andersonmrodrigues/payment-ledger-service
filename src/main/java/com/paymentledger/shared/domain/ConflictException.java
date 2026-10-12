package com.paymentledger.shared.domain;

/** The request conflicts with data that already exists. */
public abstract class ConflictException extends RuntimeException {

  protected ConflictException(String message) {
    super(message);
  }
}

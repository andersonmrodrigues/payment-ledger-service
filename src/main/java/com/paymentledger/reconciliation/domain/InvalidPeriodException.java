package com.paymentledger.reconciliation.domain;

import com.paymentledger.shared.domain.InvalidInputException;

public class InvalidPeriodException extends InvalidInputException {

  public InvalidPeriodException(String message) {
    super(message);
  }
}

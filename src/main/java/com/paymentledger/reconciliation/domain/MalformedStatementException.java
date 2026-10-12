package com.paymentledger.reconciliation.domain;

import com.paymentledger.shared.domain.InvalidInputException;

public class MalformedStatementException extends InvalidInputException {

  public MalformedStatementException(String message) {
    super(message);
  }
}

package com.paymentledger.ledger.domain;

public class DuplicateEntryException extends LedgerViolationException {

  public DuplicateEntryException(EntryReference reference) {
    super("Operation " + reference.type() + "/" + reference.id() + " is already booked");
  }
}

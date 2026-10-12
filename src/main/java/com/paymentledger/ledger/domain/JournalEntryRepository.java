package com.paymentledger.ledger.domain;

public interface JournalEntryRepository {

  /**
   * @throws DuplicateEntryException when an entry for the same reference already exists
   */
  void save(JournalEntry entry);
}

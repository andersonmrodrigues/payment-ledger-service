package com.paymentledger.ledger.domain;

import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public interface AccountRepository {

  /** Returns the account with this code and currency, opening it first if it does not exist. */
  Account getOrOpen(String code, AccountType type, Currency currency);

  /**
   * Locks the accounts for the rest of the transaction, always in id order so concurrent postings
   * on overlapping accounts cannot deadlock.
   */
  List<Account> lockAll(Collection<UUID> ids);

  void updateBalances(Collection<Account> accounts);
}

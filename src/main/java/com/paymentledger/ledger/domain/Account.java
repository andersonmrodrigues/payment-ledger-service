package com.paymentledger.ledger.domain;

import com.paymentledger.shared.domain.Money;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * A ledger account. Its balance is kept on its normal side: an asset's balance grows with debits, a
 * liability's or revenue's with credits.
 */
public final class Account {

  private final UUID id;
  private final String code;
  private final AccountType type;
  private final Currency currency;
  private Money balance;

  public Account(UUID id, String code, AccountType type, Money balance) {
    this.id = Objects.requireNonNull(id, "id");
    this.code = Objects.requireNonNull(code, "code");
    this.type = Objects.requireNonNull(type, "type");
    this.balance = Objects.requireNonNull(balance, "balance");
    this.currency = balance.currency();
  }

  public static Account open(String code, AccountType type, Currency currency) {
    return new Account(UUID.randomUUID(), code, type, Money.zero(currency));
  }

  public void apply(Posting posting) {
    if (!posting.accountId().equals(id)) {
      throw new LedgerViolationException(
          "Posting for account " + posting.accountId() + " applied to account " + id);
    }
    if (!posting.amount().currency().equals(currency)) {
      throw new LedgerViolationException(
          "Posting in " + posting.amount().currency() + " on account " + code + " in " + currency);
    }
    balance =
        posting.direction() == type.normalSide()
            ? balance.plus(posting.amount())
            : balance.minus(posting.amount());
  }

  public UUID id() {
    return id;
  }

  public String code() {
    return code;
  }

  public AccountType type() {
    return type;
  }

  public Currency currency() {
    return currency;
  }

  public Money balance() {
    return balance;
  }
}

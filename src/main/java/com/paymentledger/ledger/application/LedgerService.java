package com.paymentledger.ledger.application;

import com.paymentledger.ledger.domain.Account;
import com.paymentledger.ledger.domain.AccountRepository;
import com.paymentledger.ledger.domain.AccountType;
import com.paymentledger.ledger.domain.EntryReference;
import com.paymentledger.ledger.domain.JournalEntry;
import com.paymentledger.ledger.domain.JournalEntryRepository;
import com.paymentledger.ledger.domain.LedgerViolationException;
import com.paymentledger.ledger.domain.Posting;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

  private final AccountRepository accounts;
  private final JournalEntryRepository entries;
  private final Clock clock;

  public LedgerService(AccountRepository accounts, JournalEntryRepository entries, Clock clock) {
    this.accounts = accounts;
    this.entries = entries;
    this.clock = clock;
  }

  @Transactional
  public UUID accountId(String code, AccountType type, Currency currency) {
    return accounts.getOrOpen(code, type, currency).id();
  }

  /** Books the postings as one entry and updates the balances of the accounts involved. */
  @Transactional
  public JournalEntry post(EntryReference reference, List<Posting> postings) {
    JournalEntry entry = JournalEntry.create(reference, postings, clock.instant());
    List<UUID> accountIds = entry.postings().stream().map(Posting::accountId).distinct().toList();
    Map<UUID, Account> locked =
        accounts.lockAll(accountIds).stream()
            .collect(Collectors.toMap(Account::id, Function.identity()));
    if (locked.size() != accountIds.size()) {
      throw new LedgerViolationException("Entry " + reference + " references unknown accounts");
    }

    entry.postings().forEach(p -> locked.get(p.accountId()).apply(p));
    entries.save(entry);
    accounts.updateBalances(locked.values());
    return entry;
  }
}

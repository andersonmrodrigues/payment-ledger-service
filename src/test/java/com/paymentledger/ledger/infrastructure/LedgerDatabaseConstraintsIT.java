package com.paymentledger.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.IntegrationTest;
import com.paymentledger.ledger.application.LedgerService;
import com.paymentledger.ledger.domain.AccountType;
import com.paymentledger.ledger.domain.EntryReference;
import com.paymentledger.ledger.domain.JournalEntry;
import com.paymentledger.ledger.domain.Posting;
import com.paymentledger.shared.domain.Money;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The database keeps the ledger invariants even when the domain model is bypassed. Balance checks
 * are deferred to commit, so they surface as a failed commit rather than a failed statement.
 */
class LedgerDatabaseConstraintsIT extends IntegrationTest {

  private static final Currency BRL = Currency.getInstance("BRL");

  @Autowired LedgerService ledger;
  @Autowired TransactionTemplate tx;

  private UUID clearing;
  private UUID payable;

  @BeforeEach
  void openAccounts() {
    clearing = ledger.accountId("PSP_CLEARING", AccountType.ASSET, BRL);
    payable = ledger.accountId("MERCHANT_PAYABLE:m1", AccountType.LIABILITY, BRL);
  }

  @Test
  void rejectsAnUnbalancedEntryAtCommit() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      UUID entry = insertEntry("unbalanced");
                      insertPosting(entry, clearing, "DEBIT", 100, "BRL");
                      insertPosting(entry, payable, "CREDIT", 99, "BRL");
                    }))
        .isInstanceOf(TransactionSystemException.class)
        .rootCause()
        .hasMessageContaining("is not balanced");
    assertThat(count("journal_entries")).isZero();
  }

  @Test
  void rejectsAnEntryWithoutPostingsAtCommit() {
    assertThatThrownBy(() -> tx.executeWithoutResult(status -> insertEntry("empty")))
        .isInstanceOf(TransactionSystemException.class)
        .rootCause()
        .hasMessageContaining("needs at least two");
  }

  @Test
  void rejectsAPostingWhoseCurrencyDiffersFromItsAccount() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      UUID entry = insertEntry("currency");
                      insertPosting(entry, clearing, "DEBIT", 100, "USD");
                      insertPosting(entry, payable, "CREDIT", 100, "USD");
                    }))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void rejectsUpdatesAndDeletesOfBookedEntries() {
    JournalEntry entry =
        ledger.post(
            new EntryReference("TEST", "1"),
            List.of(
                Posting.debit(clearing, Money.of(100, "BRL")),
                Posting.credit(payable, Money.of(100, "BRL"))));

    assertThatThrownBy(() -> jdbc.sql("update postings set amount_minor = 1").update())
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.sql("delete from postings").update())
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () ->
                jdbc.sql("update journal_entries set reference_id = 'x' where id = ?")
                    .param(entry.id())
                    .update())
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThat(count("postings")).isEqualTo(2);
  }

  private UUID insertEntry(String referenceId) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "insert into journal_entries (id, reference_type, reference_id, created_at)"
                + " values (?, 'RAW', ?, ?)")
        .params(id, referenceId, Timestamp.from(Instant.now()))
        .update();
    return id;
  }

  private void insertPosting(UUID entry, UUID account, String direction, long units, String cur) {
    jdbc.sql(
            "insert into postings (entry_id, account_id, direction, amount_minor, currency)"
                + " values (?, ?, ?, ?, ?)")
        .params(entry, account, direction, units, cur)
        .update();
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}

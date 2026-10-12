package com.paymentledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.shared.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

  private static final EntryReference REF = new EntryReference("TEST", "1");
  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
  private final UUID clearing = UUID.randomUUID();
  private final UUID payable = UUID.randomUUID();
  private final UUID revenue = UUID.randomUUID();

  @Test
  void acceptsABalancedEntryWithSeveralLegs() {
    var entry =
        JournalEntry.create(
            REF,
            List.of(
                Posting.debit(clearing, Money.of(10_000, "BRL")),
                Posting.credit(payable, Money.of(9_710, "BRL")),
                Posting.credit(revenue, Money.of(290, "BRL"))),
            NOW);

    assertThat(entry.postings()).hasSize(3);
  }

  @Test
  void rejectsAnUnbalancedEntry() {
    var postings =
        List.of(
            Posting.debit(clearing, Money.of(10_000, "BRL")),
            Posting.credit(payable, Money.of(9_999, "BRL")));

    assertThatThrownBy(() -> JournalEntry.create(REF, postings, NOW))
        .isInstanceOf(LedgerViolationException.class)
        .hasMessageContaining("Unbalanced")
        .hasMessageContaining("1 BRL");
  }

  @Test
  void rejectsAnEntryBalancedOnlyAcrossCurrencies() {
    var postings =
        List.of(
            Posting.debit(clearing, Money.of(100, "BRL")),
            Posting.credit(payable, Money.of(100, "USD")));

    assertThatThrownBy(() -> JournalEntry.create(REF, postings, NOW))
        .isInstanceOf(LedgerViolationException.class)
        .hasMessageContaining("Unbalanced");
  }

  @Test
  void rejectsAnEntryWithASinglePosting() {
    var postings = List.of(Posting.debit(clearing, Money.of(100, "BRL")));

    assertThatThrownBy(() -> JournalEntry.create(REF, postings, NOW))
        .isInstanceOf(LedgerViolationException.class)
        .hasMessageContaining("at least two");
  }

  @Test
  void rejectsZeroAndNegativeAmounts() {
    assertThatThrownBy(() -> Posting.debit(clearing, Money.of(0, "BRL")))
        .isInstanceOf(LedgerViolationException.class);
    assertThatThrownBy(() -> Posting.credit(clearing, Money.of(-5, "BRL")))
        .isInstanceOf(LedgerViolationException.class);
  }

  @Test
  void cannotBeModifiedAfterCreation() {
    var entry =
        JournalEntry.create(
            REF,
            List.of(
                Posting.debit(clearing, Money.of(100, "BRL")),
                Posting.credit(payable, Money.of(100, "BRL"))),
            NOW);

    assertThatThrownBy(() -> entry.postings().add(Posting.debit(revenue, Money.of(1, "BRL"))))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

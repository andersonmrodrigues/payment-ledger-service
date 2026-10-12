package com.paymentledger.ledger.domain;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

import com.paymentledger.shared.domain.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** An immutable, balanced set of postings recording one business operation. */
public record JournalEntry(
    UUID id, EntryReference reference, List<Posting> postings, Instant createdAt) {

  public JournalEntry {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(reference, "reference");
    Objects.requireNonNull(createdAt, "createdAt");
    postings = List.copyOf(postings);
    if (postings.size() < 2) {
      throw new LedgerViolationException("A journal entry needs at least two postings");
    }
    requireBalanced(postings);
  }

  public static JournalEntry create(
      EntryReference reference, List<Posting> postings, Instant createdAt) {
    return new JournalEntry(UUID.randomUUID(), reference, postings, createdAt);
  }

  private static void requireBalanced(List<Posting> postings) {
    Map<Currency, Long> netByCurrency =
        postings.stream()
            .collect(
                groupingBy(
                    p -> p.amount().currency(),
                    reducing(0L, JournalEntry::signedMinorUnits, Math::addExact)));
    netByCurrency.forEach(
        (currency, net) -> {
          if (net != 0) {
            throw new LedgerViolationException(
                "Unbalanced journal entry: debits and credits differ by "
                    + new Money(net, currency));
          }
        });
  }

  private static long signedMinorUnits(Posting posting) {
    long units = posting.amount().minorUnits();
    return posting.direction() == Direction.DEBIT ? units : -units;
  }
}

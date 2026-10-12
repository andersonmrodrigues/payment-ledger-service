package com.paymentledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Properties that must hold for any sequence of entries, not just the hand-picked examples. */
class LedgerInvariantsTest {

  private static final Currency BRL = Currency.getInstance("BRL");
  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

  private final List<Account> accounts =
      List.of(
          Account.open("PSP_CLEARING", AccountType.ASSET, BRL),
          Account.open("MERCHANT_PAYABLE:m1", AccountType.LIABILITY, BRL),
          Account.open("MERCHANT_PAYABLE:m2", AccountType.LIABILITY, BRL),
          Account.open("FEE_REVENUE", AccountType.REVENUE, BRL));

  @Property
  void trialBalanceIsZeroAndBalancesMatchTheirPostings(
      @ForAll("balancedPostingSets") List<List<Posting>> postingSets) {
    var byId = new HashMap<UUID, Account>();
    accounts.forEach(a -> byId.put(a.id(), copyOf(a)));
    var applied = new ArrayList<Posting>();

    for (List<Posting> postings : postingSets) {
      var entry =
          JournalEntry.create(
              new EntryReference("TEST", UUID.randomUUID().toString()), postings, NOW);
      entry.postings().forEach(p -> byId.get(p.accountId()).apply(p));
      applied.addAll(entry.postings());
    }

    long debitSignedTotal = 0;
    for (Account account : byId.values()) {
      long sign = account.type().normalSide() == Direction.DEBIT ? 1 : -1;
      debitSignedTotal += sign * account.balance().minorUnits();
      assertThat(account.balance()).isEqualTo(sumOfPostings(account, applied));
    }
    assertThat(debitSignedTotal).as("trial balance").isZero();
  }

  @Property
  void anyEntryWhoseSidesDifferIsRejected(
      @ForAll("balancedPostingSet") List<Posting> balanced,
      @ForAll @IntRange(min = 1, max = 1_000) int drift) {
    var first = balanced.getFirst();
    var skewed = new ArrayList<>(balanced);
    skewed.set(
        0,
        new Posting(
            first.accountId(), first.direction(), first.amount().plus(new Money(drift, BRL))));

    assertThatThrownBy(() -> JournalEntry.create(new EntryReference("TEST", "x"), skewed, NOW))
        .isInstanceOf(LedgerViolationException.class);
  }

  @Provide
  Arbitrary<List<List<Posting>>> balancedPostingSets() {
    return balancedPostingSet().list().ofMaxSize(30);
  }

  /** Random debits on some accounts, with their total split into random credits on others. */
  @Provide
  Arbitrary<List<Posting>> balancedPostingSet() {
    Arbitrary<Account> account = Arbitraries.of(accounts);
    Arbitrary<Long> amount = Arbitraries.longs().between(1, 1_000_000);
    Arbitrary<List<Posting>> debits =
        Combinators.combine(account, amount)
            .as((a, units) -> Posting.debit(a.id(), new Money(units, BRL)))
            .list()
            .ofMinSize(1)
            .ofMaxSize(4);
    return debits.flatMap(
        ds -> {
          long total = ds.stream().mapToLong(p -> p.amount().minorUnits()).sum();
          int maxLegs = (int) Math.min(4, total);
          return Arbitraries.integers()
              .between(1, maxLegs)
              .flatMap(legs -> splits(total, legs))
              .flatMap(
                  parts ->
                      account
                          .list()
                          .ofSize(parts.size())
                          .map(
                              targets -> {
                                var postings = new ArrayList<>(ds);
                                for (int i = 0; i < parts.size(); i++) {
                                  postings.add(
                                      Posting.credit(
                                          targets.get(i).id(), new Money(parts.get(i), BRL)));
                                }
                                return postings;
                              }));
        });
  }

  /** Splits total into the given number of strictly positive parts. */
  private static Arbitrary<List<Long>> splits(long total, int legs) {
    if (legs == 1) {
      return Arbitraries.just(List.of(total));
    }
    return Arbitraries.longs()
        .between(1, total - 1)
        .set()
        .ofSize(legs - 1)
        .map(
            cuts -> {
              var sorted = new ArrayList<>(cuts);
              sorted.sort(null);
              sorted.add(total);
              var parts = new ArrayList<Long>();
              long previous = 0;
              for (long cut : sorted) {
                parts.add(cut - previous);
                previous = cut;
              }
              return parts;
            });
  }

  private static Money sumOfPostings(Account account, List<Posting> postings) {
    Money sum = Money.zero(account.currency());
    for (Posting p : postings) {
      if (p.accountId().equals(account.id())) {
        sum =
            p.direction() == account.type().normalSide()
                ? sum.plus(p.amount())
                : sum.minus(p.amount());
      }
    }
    return sum;
  }

  private static Account copyOf(Account account) {
    return new Account(account.id(), account.code(), account.type(), account.balance());
  }
}

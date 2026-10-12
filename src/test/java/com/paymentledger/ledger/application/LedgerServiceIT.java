package com.paymentledger.ledger.application;

import static com.paymentledger.LedgerAssertions.assertLedgerConsistent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.IntegrationTest;
import com.paymentledger.ledger.domain.AccountType;
import com.paymentledger.ledger.domain.DuplicateEntryException;
import com.paymentledger.ledger.domain.EntryReference;
import com.paymentledger.ledger.domain.LedgerViolationException;
import com.paymentledger.ledger.domain.Posting;
import com.paymentledger.shared.domain.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LedgerServiceIT extends IntegrationTest {

  private static final Currency BRL = Currency.getInstance("BRL");

  @Autowired LedgerService ledger;

  private UUID clearing;
  private UUID payable;

  @BeforeEach
  void openAccounts() {
    clearing = ledger.accountId("PSP_CLEARING", AccountType.ASSET, BRL);
    payable = ledger.accountId("MERCHANT_PAYABLE:m1", AccountType.LIABILITY, BRL);
  }

  @Test
  void booksAnEntryAndUpdatesBalances() {
    ledger.post(ref("1"), transfer(clearing, payable, 10_000));

    assertThat(balanceOf(clearing)).isEqualTo(10_000);
    assertThat(balanceOf(payable)).isEqualTo(10_000);
    assertLedgerConsistent(jdbc);
  }

  @Test
  void opensAnAccountOnlyOnce() {
    assertThat(ledger.accountId("PSP_CLEARING", AccountType.ASSET, BRL)).isEqualTo(clearing);
  }

  @Test
  void rejectsBookingTheSameOperationTwice() {
    ledger.post(ref("1"), transfer(clearing, payable, 10_000));

    assertThatThrownBy(() -> ledger.post(ref("1"), transfer(clearing, payable, 10_000)))
        .isInstanceOf(DuplicateEntryException.class);
    assertThat(balanceOf(clearing)).isEqualTo(10_000);
    assertLedgerConsistent(jdbc);
  }

  @Test
  void rejectsAnEntryOnAnUnknownAccountWithoutSideEffects() {
    assertThatThrownBy(() -> ledger.post(ref("1"), transfer(clearing, UUID.randomUUID(), 100)))
        .isInstanceOf(LedgerViolationException.class);
    assertThat(jdbc.sql("select count(*) from journal_entries").query(Long.class).single())
        .isZero();
    assertThat(balanceOf(clearing)).isZero();
  }

  @Test
  void rejectsAPostingInAnotherCurrencyWithoutSideEffects() {
    var postings =
        List.of(
            Posting.debit(clearing, Money.of(100, "USD")),
            Posting.credit(payable, Money.of(100, "USD")));

    assertThatThrownBy(() -> ledger.post(ref("1"), postings))
        .isInstanceOf(LedgerViolationException.class);
    assertThat(jdbc.sql("select count(*) from postings").query(Long.class).single()).isZero();
  }

  /**
   * Half the threads move money one way and half the other way, so they lock the same two rows in
   * opposite posting order. Ordered locking must prevent both lost updates and deadlocks.
   */
  @Test
  void concurrentPostingsOnTheSameAccountsLoseNothingAndDoNotDeadlock() throws Exception {
    ledger.post(ref("seed"), transfer(clearing, payable, 1_000_000));
    int threads = 50;
    var start = new CountDownLatch(1);
    List<Future<?>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        String id = Integer.toString(i);
        boolean forward = i % 2 == 0;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return forward
                      ? ledger.post(ref(id), transfer(clearing, payable, 100))
                      : ledger.post(ref(id), transfer(payable, clearing, 30));
                }));
      }
      start.countDown();
      for (Future<?> result : results) {
        result.get();
      }
    }

    // 25 forward transfers of 100 grow both balances; 25 reverse transfers of 30 shrink both.
    long expected = 1_000_000 + 25 * 100 - 25 * 30;
    assertThat(balanceOf(clearing)).isEqualTo(expected);
    assertThat(balanceOf(payable)).isEqualTo(expected);
    assertLedgerConsistent(jdbc);
  }

  private static EntryReference ref(String id) {
    return new EntryReference("TEST", id);
  }

  /** Debit one account and credit the other, as the domain sees it. */
  private static List<Posting> transfer(UUID debit, UUID credit, long units) {
    return List.of(
        Posting.debit(debit, new Money(units, BRL)), Posting.credit(credit, new Money(units, BRL)));
  }

  private long balanceOf(UUID account) {
    return jdbc.sql("select balance_minor from accounts where id = ?")
        .param(account)
        .query(Long.class)
        .single();
  }
}

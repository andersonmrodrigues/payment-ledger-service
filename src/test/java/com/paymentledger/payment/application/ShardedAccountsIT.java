package com.paymentledger.payment.application;

import static com.paymentledger.LedgerAssertions.assertLedgerConsistent;
import static com.paymentledger.LedgerAssertions.shardedBalance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.paymentledger.IntegrationTest;
import com.paymentledger.ledger.application.LedgerService;
import com.paymentledger.ledger.domain.AccountShards;
import com.paymentledger.outbox.application.OutboxRecorder;
import com.paymentledger.payment.domain.Payment;
import com.paymentledger.payment.domain.PaymentRepository;
import com.paymentledger.reconciliation.application.ReconciliationService;
import com.paymentledger.reconciliation.domain.OperationType;
import com.paymentledger.reconciliation.domain.ReconciliationPeriod;
import com.paymentledger.reconciliation.domain.StatementLine;
import com.paymentledger.shared.domain.Money;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ShardedAccountsIT extends IntegrationTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-10-12");

  @Autowired PaymentService payments;
  @Autowired PaymentRepository repository;
  @Autowired LedgerService ledger;
  @Autowired OutboxRecorder outbox;
  @Autowired ReconciliationService reconciliations;
  @Autowired TransactionTemplate tx;

  /**
   * One capture keeps its transaction open while eight others start. Without sharding all eight
   * wait on the clearing row; with 16 shards the chance that all of them collide is 2^-32.
   */
  @Test
  void capturesOfDifferentPaymentsDoNotWaitForEachOther() throws Exception {
    var holding = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(9)) {
      Future<?> held =
          pool.submit(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        payments.capture(CHECKOUT, "m1", Money.of(10_000, "BRL"), "psp-held");
                        holding.countDown();
                        awaitQuietly(release);
                      }));
      assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

      List<Future<?>> others = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        String reference = "psp-" + i;
        others.add(
            pool.submit(() -> payments.capture(CHECKOUT, "m1", Money.of(1_000, "BRL"), reference)));
      }
      try {
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> others.stream().anyMatch(Future::isDone));
      } finally {
        release.countDown();
      }
      held.get(10, TimeUnit.SECONDS);
      for (Future<?> other : others) {
        other.get(10, TimeUnit.SECONDS);
      }
    }

    assertThat(shardedBalance(jdbc, PaymentBookkeeping.PSP_CLEARING)).isEqualTo(18_000);
    assertLedgerConsistent(jdbc);
  }

  /** A refund booked after the shard count changed may hit another shard; totals hold. */
  @Test
  void totalsAndReconciliationHoldWhenTheShardCountChanges() {
    PaymentService oneShard = withShards(1);
    Payment payment = captureOutsideShardZeroOf16(oneShard);

    payments.refund(CHECKOUT, payment.id(), 10_000, "psp-refund");

    assertLedgerConsistent(jdbc);
    assertThat(shardedBalance(jdbc, PaymentBookkeeping.PSP_CLEARING)).isZero();
    assertThat(shardedBalance(jdbc, PaymentBookkeeping.merchantPayableCode("m1"))).isZero();
    assertThat(shardedBalance(jdbc, PaymentBookkeeping.FEE_REVENUE)).isZero();
    assertThat(
            jdbc.sql("select count(*) from accounts where balance_minor < 0")
                .query(Long.class)
                .single())
        .as("the refund landed on other shards than its capture")
        .isPositive();

    var report =
        reconciliations
            .reconcile(
                new ReconciliationPeriod(TODAY, TODAY.plusDays(1)),
                List.of(
                    line(2, payment.pspReference(), OperationType.CAPTURE),
                    line(3, "psp-refund", OperationType.REFUND)))
            .report();
    assertThat(report.matched()).isEqualTo(2);
    assertThat(report.discrepancies()).isEmpty();
  }

  private PaymentService withShards(int count) {
    return new PaymentService(
        repository, new PaymentBookkeeping(ledger, count), outbox, 290, clock);
  }

  /** Shard 0 is where a one-shard split books; avoid it so the refund must move. */
  private Payment captureOutsideShardZeroOf16(PaymentService service) {
    var sixteen = new AccountShards(16);
    for (int attempt = 0; ; attempt++) {
      String reference = "psp-capture-" + attempt;
      Payment payment =
          tx.execute(
              status -> {
                Payment captured =
                    service.capture(CHECKOUT, "m1", Money.of(10_000, "BRL"), reference);
                if (sixteen.code("X", captured.id()).equals("X#0")) {
                  status.setRollbackOnly();
                }
                return captured;
              });
      if (!sixteen.code("X", payment.id()).equals("X#0")) {
        return payment;
      }
    }
  }

  private static StatementLine line(int number, String reference, OperationType type) {
    return new StatementLine(number, reference, type, Money.of(10_000, "BRL"), TODAY);
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

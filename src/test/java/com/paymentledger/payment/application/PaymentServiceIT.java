package com.paymentledger.payment.application;

import static com.paymentledger.LedgerAssertions.assertLedgerConsistent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.IntegrationTest;
import com.paymentledger.payment.domain.DuplicatePspReferenceException;
import com.paymentledger.payment.domain.Payment;
import com.paymentledger.payment.domain.PaymentNotFoundException;
import com.paymentledger.payment.domain.PaymentRuleViolationException;
import com.paymentledger.payment.domain.PaymentStatus;
import com.paymentledger.shared.domain.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentServiceIT extends IntegrationTest {

  @Autowired PaymentService payments;

  @Test
  void captureBooksClearingMerchantAndFee() {
    payments.capture("m1", Money.of(10_000, "BRL"), "psp-1");

    assertThat(balance("PSP_CLEARING")).isEqualTo(10_000);
    assertThat(balance("MERCHANT_PAYABLE:m1")).isEqualTo(9_710);
    assertThat(balance("FEE_REVENUE")).isEqualTo(290);
    assertLedgerConsistent(jdbc);
  }

  @Test
  void fullRefundReversesEveryBalance() {
    Payment payment = payments.capture("m1", Money.of(10_000, "BRL"), "psp-1");

    payments.refund(payment.id(), Money.of(4_000, "BRL"), "psp-r1");
    payments.refund(payment.id(), Money.of(6_000, "BRL"), "psp-r2");

    assertThat(payments.get(payment.id()).status()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(balance("PSP_CLEARING")).isZero();
    assertThat(balance("MERCHANT_PAYABLE:m1")).isZero();
    assertThat(balance("FEE_REVENUE")).isZero();
    assertLedgerConsistent(jdbc);
  }

  @Test
  void rejectsAReusedPspReferenceWithoutBookingAnything() {
    payments.capture("m1", Money.of(10_000, "BRL"), "psp-1");

    assertThatThrownBy(() -> payments.capture("m1", Money.of(10_000, "BRL"), "psp-1"))
        .isInstanceOf(DuplicatePspReferenceException.class);
    assertThat(count("payments")).isEqualTo(1);
    assertThat(count("journal_entries")).isEqualTo(1);
    assertThat(balance("PSP_CLEARING")).isEqualTo(10_000);
  }

  @Test
  void rejectsARefundOfAnUnknownPayment() {
    assertThatThrownBy(() -> payments.refund(UUID.randomUUID(), Money.of(1, "BRL"), "r"))
        .isInstanceOf(PaymentNotFoundException.class);
  }

  /**
   * Ten refunds of 3000 race on a 10000 payment. The payment row lock serializes them, so exactly
   * three succeed and the rest are refused: never a lost update, never an over-refund.
   */
  @Test
  void racingRefundsNeverExceedTheCapturedAmount() throws Exception {
    Payment payment = payments.capture("m1", Money.of(10_000, "BRL"), "psp-1");
    int threads = 10;
    var start = new CountDownLatch(1);
    List<Future<?>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        String reference = "psp-r" + i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return payments.refund(payment.id(), Money.of(3_000, "BRL"), reference);
                }));
      }
      start.countDown();
    }

    int succeeded = 0;
    for (Future<?> result : results) {
      try {
        result.get();
        succeeded++;
      } catch (ExecutionException e) {
        assertThat(e.getCause()).isInstanceOf(PaymentRuleViolationException.class);
      }
    }
    assertThat(succeeded).isEqualTo(3);
    assertThat(payments.get(payment.id()).refundedAmount()).isEqualTo(Money.of(9_000, "BRL"));
    assertThat(balance("PSP_CLEARING")).isEqualTo(1_000);
    assertLedgerConsistent(jdbc);
  }

  private long balance(String code) {
    return jdbc.sql("select balance_minor from accounts where code = ?")
        .param(code)
        .query(Long.class)
        .single();
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}

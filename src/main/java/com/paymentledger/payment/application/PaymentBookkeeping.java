package com.paymentledger.payment.application;

import com.paymentledger.ledger.application.LedgerService;
import com.paymentledger.ledger.domain.AccountShards;
import com.paymentledger.ledger.domain.AccountType;
import com.paymentledger.ledger.domain.EntryReference;
import com.paymentledger.ledger.domain.Posting;
import com.paymentledger.payment.domain.Payment;
import com.paymentledger.payment.domain.Refund;
import com.paymentledger.shared.domain.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Books payment operations in the ledger.
 *
 * <pre>
 * capture: D PSP_CLEARING amount       / C MERCHANT_PAYABLE amount - fee / C FEE_REVENUE fee
 * refund:  D MERCHANT_PAYABLE amount - feeReturned / D FEE_REVENUE feeReturned / C PSP_CLEARING amount
 * </pre>
 *
 * Every capture would otherwise lock the same clearing and fee rows, and a large merchant's
 * payable, so each of these accounts is sharded by payment id. A refund lands on the shards of its
 * capture.
 */
@Component
public class PaymentBookkeeping {

  public static final String PSP_CLEARING = "PSP_CLEARING";
  public static final String FEE_REVENUE = "FEE_REVENUE";
  public static final String CAPTURE = "PAYMENT_CAPTURE";
  public static final String REFUND = "PAYMENT_REFUND";

  private final LedgerService ledger;
  private final AccountShards shards;

  public PaymentBookkeeping(
      LedgerService ledger, @Value("${ledger.account-shards}") int accountShards) {
    this.ledger = ledger;
    this.shards = new AccountShards(accountShards);
  }

  public void bookCapture(Payment payment) {
    Money toMerchant = payment.amount().minus(payment.fee());
    var postings = new ArrayList<Posting>();
    postings.add(Posting.debit(clearing(payment), payment.amount()));
    addCredit(postings, merchantPayable(payment), toMerchant);
    addCredit(postings, feeRevenue(payment), payment.fee());
    ledger.post(new EntryReference(CAPTURE, payment.id().toString()), postings);
  }

  public void bookRefund(Payment payment, Refund refund) {
    Money fromMerchant = refund.amount().minus(refund.feeReturned());
    var postings = new ArrayList<Posting>();
    addDebit(postings, merchantPayable(payment), fromMerchant);
    addDebit(postings, feeRevenue(payment), refund.feeReturned());
    postings.add(Posting.credit(clearing(payment), refund.amount()));
    ledger.post(new EntryReference(REFUND, refund.id().toString()), postings);
  }

  public static String merchantPayableCode(String merchantId) {
    return "MERCHANT_PAYABLE:" + merchantId;
  }

  private UUID clearing(Payment payment) {
    return ledger.accountId(
        shards.code(PSP_CLEARING, payment.id()), AccountType.ASSET, currency(payment));
  }

  private UUID merchantPayable(Payment payment) {
    return ledger.accountId(
        shards.code(merchantPayableCode(payment.merchantId()), payment.id()),
        AccountType.LIABILITY,
        currency(payment));
  }

  private UUID feeRevenue(Payment payment) {
    return ledger.accountId(
        shards.code(FEE_REVENUE, payment.id()), AccountType.REVENUE, currency(payment));
  }

  private static Currency currency(Payment payment) {
    return payment.amount().currency();
  }

  // A zero fee (or a refund that returns no fee) has no leg: postings must be positive.
  private static void addCredit(List<Posting> postings, UUID account, Money amount) {
    if (amount.isPositive()) {
      postings.add(Posting.credit(account, amount));
    }
  }

  private static void addDebit(List<Posting> postings, UUID account, Money amount) {
    if (amount.isPositive()) {
      postings.add(Posting.debit(account, amount));
    }
  }
}

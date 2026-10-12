package com.paymentledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.shared.domain.Money;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

  private static final Currency BRL = Currency.getInstance("BRL");

  @Test
  void assetGrowsWithDebitsAndShrinksWithCredits() {
    var asset = Account.open("PSP_CLEARING", AccountType.ASSET, BRL);

    asset.apply(Posting.debit(asset.id(), Money.of(1_000, "BRL")));
    asset.apply(Posting.credit(asset.id(), Money.of(300, "BRL")));

    assertThat(asset.balance()).isEqualTo(Money.of(700, "BRL"));
  }

  @Test
  void liabilityGrowsWithCreditsAndShrinksWithDebits() {
    var liability = Account.open("MERCHANT_PAYABLE:m1", AccountType.LIABILITY, BRL);

    liability.apply(Posting.credit(liability.id(), Money.of(1_000, "BRL")));
    liability.apply(Posting.debit(liability.id(), Money.of(300, "BRL")));

    assertThat(liability.balance()).isEqualTo(Money.of(700, "BRL"));
  }

  @Test
  void rejectsAPostingInAnotherCurrency() {
    var account = Account.open("PSP_CLEARING", AccountType.ASSET, BRL);

    assertThatThrownBy(() -> account.apply(Posting.debit(account.id(), Money.of(1, "USD"))))
        .isInstanceOf(LedgerViolationException.class);
    assertThat(account.balance()).isEqualTo(Money.zero(BRL));
  }

  @Test
  void rejectsAPostingAddressedToAnotherAccount() {
    var account = Account.open("PSP_CLEARING", AccountType.ASSET, BRL);

    assertThatThrownBy(() -> account.apply(Posting.debit(UUID.randomUUID(), Money.of(1, "BRL"))))
        .isInstanceOf(LedgerViolationException.class);
  }
}

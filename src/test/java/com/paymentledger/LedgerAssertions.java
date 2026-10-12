package com.paymentledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Ledger-wide invariants checked straight against the database. */
public final class LedgerAssertions {

  private LedgerAssertions() {}

  public static void assertLedgerConsistent(JdbcClient jdbc) {
    var nonZeroTrialBalances =
        jdbc.sql(
                """
                select currency,
                       sum(case direction when 'DEBIT' then amount_minor else -amount_minor end)
                from postings group by currency
                having sum(case direction when 'DEBIT' then amount_minor else -amount_minor end) <> 0
                """)
            .query()
            .listOfRows();
    assertThat(nonZeroTrialBalances).as("trial balance per currency").isEmpty();

    var driftedAccounts =
        jdbc.sql(
                """
                select a.code, a.balance_minor, coalesce(sum(
                         case when (p.direction = 'DEBIT') = (a.type = 'ASSET')
                              then p.amount_minor else -p.amount_minor end), 0) as from_postings
                from accounts a left join postings p on p.account_id = a.id
                group by a.id
                having a.balance_minor <> coalesce(sum(
                         case when (p.direction = 'DEBIT') = (a.type = 'ASSET')
                              then p.amount_minor else -p.amount_minor end), 0)
                """)
            .query()
            .listOfRows();
    assertThat(driftedAccounts).as("stored balance equals the sum of postings").isEmpty();
  }
}

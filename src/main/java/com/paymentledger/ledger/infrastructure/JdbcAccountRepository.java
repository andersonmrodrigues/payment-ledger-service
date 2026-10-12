package com.paymentledger.ledger.infrastructure;

import com.paymentledger.ledger.domain.Account;
import com.paymentledger.ledger.domain.AccountRepository;
import com.paymentledger.ledger.domain.AccountType;
import com.paymentledger.shared.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

  private final JdbcClient jdbc;

  JdbcAccountRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Account getOrOpen(String code, AccountType type, Currency currency) {
    jdbc.sql(
            """
            insert into accounts (id, code, type, currency) values (?, ?, ?, ?)
            on conflict (code, currency) do nothing
            """)
        .params(UUID.randomUUID(), code, type.name(), currency.getCurrencyCode())
        .update();
    Account account =
        jdbc.sql("select * from accounts where code = ? and currency = ?")
            .params(code, currency.getCurrencyCode())
            .query(JdbcAccountRepository::map)
            .single();
    if (account.type() != type) {
      throw new IllegalStateException(
          "Account " + code + " exists as " + account.type() + ", requested as " + type);
    }
    return account;
  }

  @Override
  public List<Account> lockAll(Collection<UUID> ids) {
    return jdbc.sql("select * from accounts where id = any(?) order by id for update")
        .param(ids.toArray(UUID[]::new))
        .query(JdbcAccountRepository::map)
        .list();
  }

  @Override
  public void updateBalances(Collection<Account> accounts) {
    for (Account account : accounts) {
      jdbc.sql("update accounts set balance_minor = ? where id = ?")
          .params(account.balance().minorUnits(), account.id())
          .update();
    }
  }

  private static Account map(ResultSet rs, int row) throws SQLException {
    Currency currency = Currency.getInstance(rs.getString("currency"));
    return new Account(
        rs.getObject("id", UUID.class),
        rs.getString("code"),
        AccountType.valueOf(rs.getString("type")),
        new Money(rs.getLong("balance_minor"), currency));
  }
}

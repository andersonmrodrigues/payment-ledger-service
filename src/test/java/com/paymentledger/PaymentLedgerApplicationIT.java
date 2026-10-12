package com.paymentledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentLedgerApplicationIT {

  @Autowired JdbcClient jdbc;

  @Test
  void connectsToPostgres() {
    assertThat(jdbc.sql("select version()").query(String.class).single()).contains("PostgreSQL");
  }
}

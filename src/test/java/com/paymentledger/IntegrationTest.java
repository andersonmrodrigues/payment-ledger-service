package com.paymentledger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Base for tests against a real PostgreSQL; every test starts from empty tables. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

  @Autowired protected JdbcClient jdbc;

  @BeforeEach
  void truncateTables() {
    // TRUNCATE bypasses the append-only row triggers, which is what a test reset needs.
    String tables =
        String.join(
            ", ",
            jdbc.sql(
                    """
                    select tablename from pg_tables
                    where schemaname = 'public' and tablename <> 'flyway_schema_history'
                    """)
                .query(String.class)
                .list());
    jdbc.sql("truncate table " + tables + " restart identity cascade").update();
  }
}

package com.paymentledger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base for tests against a real PostgreSQL and a running server; every test starts from empty
 * tables.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "scheduling.enabled=false",
      "outbox.retry.max-attempts=3",
      // tiny batches so retention tests go through the batching loop
      "outbox.retention.batch-size=2",
      "idempotency.retention.batch-size=2"
    })
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

  @Autowired protected JdbcClient jdbc;
  @Autowired protected TestRestTemplate http;
  @Autowired protected MutableClock clock;
  @Autowired protected RecordingEventPublisher publisher;

  @BeforeEach
  void resetState() {
    clock.reset();
    publisher.reset();
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

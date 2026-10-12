package com.paymentledger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
      "idempotency.retention.batch-size=2",
      "management.server.port=0",
      "security.clients[0].id=" + IntegrationTest.CHECKOUT,
      "security.clients[0].key-sha256="
          + "b980c273cf5c68e1acd15f8ceb3f90793d8a504132d4b7f65d721ee2253f2e2d",
      "security.clients[0].roles=PAYMENTS",
      "security.clients[1].id=marketplace",
      "security.clients[1].key-sha256="
          + "e83ac44f04b5c7f91a4b2130548b4a6476c33fa2e6cdb5056cd75d34b299173c",
      "security.clients[1].roles=PAYMENTS",
      "security.clients[2].id=back-office",
      "security.clients[2].key-sha256="
          + "e52f867269d5a795a8a9710253e8cee4aa38c1a66602a00aad0f4825ad8dbde8",
      "security.clients[2].roles=BACK_OFFICE"
    })
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

  /** The client most tests act as; its key is {@link #CHECKOUT_KEY}. */
  protected static final String CHECKOUT = "checkout";

  protected static final String CHECKOUT_KEY = "checkout-test-key";
  protected static final String MARKETPLACE_KEY = "marketplace-test-key";
  protected static final String BACK_OFFICE_KEY = "back-office-test-key";

  @Autowired protected JdbcClient jdbc;
  @Autowired protected MutableClock clock;
  @Autowired protected RecordingEventPublisher publisher;
  @Autowired private RestTemplateBuilder restTemplates;
  @LocalServerPort private int port;

  /** Calls the API as the checkout client (role PAYMENTS). */
  protected TestRestTemplate http;

  /** Calls the API as the back office client (role BACK_OFFICE). */
  protected TestRestTemplate backOffice;

  /** Calls the API without credentials. */
  protected TestRestTemplate anonymous;

  @BeforeEach
  void createClients() {
    http = as(CHECKOUT_KEY);
    backOffice = as(BACK_OFFICE_KEY);
    anonymous = new TestRestTemplate(restTemplates.rootUri("http://localhost:" + port));
  }

  protected TestRestTemplate as(String apiKey) {
    return new TestRestTemplate(
        restTemplates
            .rootUri("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey));
  }

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

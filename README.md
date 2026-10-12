# payment-ledger-service

Captures and refunds card payments, books every money movement in a double-entry ledger,
publishes events through a transactional outbox, and reconciles the PSP's settlement statement
against both the payments and the ledger.

Java 21, Spring Boot 3.5, PostgreSQL 17, Flyway, plain JDBC (`JdbcClient`).

## Running it

```bash
docker compose up -d                       # PostgreSQL on localhost:5432
DB_PASSWORD=payment_ledger ./scripts/mvn spring-boot:run
```

`scripts/mvn` runs Maven on JDK 21 when the shell defaults to an older one.

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/payment_ledger` |
| `DB_USERNAME` | `payment_ledger` |
| `DB_PASSWORD` | empty |

Other settings live in `application.yml`: the fee (`payment.fee-basis-points`, 290 = 2.90%),
the relay (`outbox.relay.*`) and the retry policy (`outbox.retry.*`).

## Tests

```bash
./scripts/mvn verify     # formatting check, unit tests, integration and e2e tests
```

Integration tests need Docker; Testcontainers starts PostgreSQL. `./scripts/mvn spotless:apply`
fixes formatting.

## API

```bash
# capture
curl -X POST localhost:8080/payments \
  -H 'Idempotency-Key: 7f9c2b0e-capture' -H 'Content-Type: application/json' \
  -d '{"merchantId":"merchant-42","amountMinor":15990,"currency":"BRL","pspReference":"ch_3Q8xYz"}'

# partial refund
curl -X POST localhost:8080/payments/{paymentId}/refunds \
  -H 'Idempotency-Key: 7f9c2b0e-refund-1' -H 'Content-Type: application/json' \
  -d '{"amountMinor":5000,"pspReference":"re_1Pq7Ab"}'

curl localhost:8080/payments/{paymentId}

# reconcile a day
curl -X POST 'localhost:8080/reconciliations?periodStart=2026-10-12&periodEnd=2026-10-13' \
  -F statement=@settlement-2026-10-12.csv
curl localhost:8080/reconciliations/{id}

# send dead events again, or re-send a window to a consumer that lost data
curl -X POST localhost:8080/outbox/replay -H 'Content-Type: application/json' \
  -d '{"eventIds":["..."]}'
curl -X POST localhost:8080/outbox/replay -H 'Content-Type: application/json' \
  -d '{"from":"2026-10-12T00:00:00Z","to":"2026-10-13T00:00:00Z"}'
```

Amounts are always integer minor units (cents). Errors are RFC 9457 problem details:

| Status | When |
|---|---|
| 400 | invalid body, header or period; malformed statement (the detail names the line) |
| 404 | unknown payment or reconciliation |
| 409 | PSP reference already used by another operation |
| 422 | refund over the refundable amount; idempotency key reused with a different body |

Operational endpoints: `/actuator/health/liveness`, `/actuator/health/readiness`,
`/actuator/prometheus`.

## Design

One package per bounded context, each split into `domain`, `application`, `infrastructure` and
`web`. Domain code has no Spring or JDBC in it. `ArchitectureTest` enforces this and the
direction of dependencies:

```
payment ──> ledger
   │
   └──────> outbox            reconciliation ──> payment, ledger
idempotency (used by web only)
```

### Ledger

Accounts are created per currency on first use: `PSP_CLEARING` (asset, money the PSP owes us),
`MERCHANT_PAYABLE:<merchantId>` (liability, money we owe the merchant) and `FEE_REVENUE`.

| Operation | Debit | Credit |
|---|---|---|
| Capture | PSP clearing: amount | merchant payable: amount − fee; fee revenue: fee |
| Refund | merchant payable: amount − fee returned; fee revenue: fee returned | PSP clearing: amount |

The fee is returned pro rata on each refund, rounded half-even, and clamped so the merchant is
never charged more fee than the amount left. After a full refund every account returns to zero.

### Invariants and where they hold

Each rule is checked in the domain and again by PostgreSQL, so a bug or a manual `INSERT`
cannot break it.

| Invariant | Domain | Database |
|---|---|---|
| Every entry balances per currency, with at least two postings | `JournalEntry` | deferred constraint trigger, checked at commit |
| Entries and postings never change | no update methods | triggers reject `UPDATE`/`DELETE` |
| A posting's currency is its account's | `Account.apply` | composite foreign key `(account_id, currency)` |
| One entry per business operation | `DuplicateEntryException` | unique `(reference_type, reference_id)` |
| One operation per PSP reference | `DuplicatePspReferenceException` | unique `psp_reference` on payments and refunds |
| Refunds never exceed the capture | `Payment.refund` | payment row locked for the refund |
| Stored balance equals the sum of postings | `Account.apply` under row lock | checked in tests after every scenario |

### Concurrency

- Postings lock their accounts with `SELECT … FOR UPDATE`, always in id order, so concurrent
  transfers between the same accounts cannot deadlock.
- A refund locks its payment row, so concurrent refunds see each other.
- The idempotency key is inserted (`ON CONFLICT DO NOTHING`) in the same transaction as the
  operation. A concurrent duplicate waits on that row and then returns the stored response
  with `Idempotent-Replayed: true`. If the operation fails, the key goes with the rollback and
  the client can retry with it.

### Outbox

`PaymentCaptured` and `PaymentRefunded` are written in the same transaction as the payment and
its journal entry. `OutboxRecorder` requires an active transaction and fails otherwise.

The relay leases a batch in one statement (`FOR UPDATE SKIP LOCKED`, then pushes the events'
next attempt past `outbox.relay.lease` and stamps a lease token), so any number of instances can
run it. It holds no transaction or row lock while it calls the broker. Each outcome is written on
its own and only while the token still matches. If a relay dies mid-batch, its events go out
when the lease runs out. A relay whose lease expired cannot overwrite what the next one
recorded. A failed publish sets the next attempt with exponential backoff (1s doubling, capped
at 5m). After `max-attempts` the event becomes `DEAD`. `LoggingEventPublisher` stands in for
a broker. A Kafka or SNS adapter would implement `EventPublisher`.

Delivery is **at least once**: a crash between publish and commit sends the event again, with
the same id. Consumers must deduplicate on the event id. Events go out in the order they were
recorded (an identity column, not the timestamp, which can tie). With several relays running,
two events of one payment can be in flight at once, so consumers that care about order should
compare `capturedAt`/`refundedAt`.

### Reconciliation

The statement is a CSV:

```
psp_reference,type,amount_minor,currency,settled_on
ch_3Q8xYz,CAPTURE,15990,BRL,2026-10-12
re_1Pq7Ab,REFUND,5000,BRL,2026-10-12
```

The whole file is rejected if any line is malformed, so a partial upload can't make half the
statement look missing. Each line is matched by PSP reference and type against the operation
and against the amount its journal entry moved through PSP clearing. The period is
`[periodStart, periodEnd)` in UTC, at most 31 days.

| Result | Meaning |
|---|---|
| matched | statement, operation and ledger agree |
| `MISSING_IN_SERVICE` | the PSP settled something we never recorded |
| `MISSING_IN_STATEMENT` | we recorded an operation in the period that the PSP did not settle |
| `AMOUNT_MISMATCH`, `CURRENCY_MISMATCH` | statement and operation disagree |
| `LEDGER_MISMATCH` | the operation has no journal entry, or it moved a different amount |
| `DUPLICATE_IN_STATEMENT` | the same operation appears twice in the file |

Lines may settle operations captured before the period, since PSPs settle a day or more later.
Every run and its discrepancies are stored for audit.

## Running it in production

- **Alerts.** `outbox_events{status="DEAD"} > 0` means events were given up on. Fix the cause,
  then replay them. A growing `PENDING` count means the relay or the broker is behind.
- **Retention.** Daily jobs delete published outbox events after `outbox.retention.period`
  (30 days, which is also how far back a replay can reach) and idempotency keys after
  `idempotency.retention.period` (7 days, how long clients may retry with a key). Dead events
  are kept. A capture retried after its key is forgotten still gets `409`, because the PSP
  reference is unique.
- **Relay lease.** `outbox.relay.lease` must exceed the time to publish a whole batch. A shorter
  lease makes another relay send the batch again. That is still at-least-once, but it is
  wasted work.
- **Dedicated relays.** `outbox.relay.enabled=false` keeps an instance from relaying.
  `scheduling.enabled=false` turns off every background job.
- **Access.** There is no authentication. `/outbox/replay` and `/reconciliations` are back
  office operations. Put them behind your gateway's auth before exposing the service.

## Not included

Currency conversion, merchant payouts, chargebacks, authentication, a real PSP or broker, and
list or search endpoints.

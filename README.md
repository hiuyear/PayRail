# PayRail

A payment service built around one guarantee: a retried request never charges a customer twice.

Stripe moves the money. PayRail owns the record of it, which means idempotent request handling, a double entry ledger, and honest failure states.

## The problem

Payment APIs get retried. The client times out, a proxy replays the request, a user double clicks. The obvious way to handle that is to check whether you have seen the request before:

```
SELECT ... WHERE idempotency_key = ?    not found
call stripe                              money moves
INSERT the payment row                   unique constraint fires
```

That is broken under concurrency. Two requests arriving together both pass the SELECT, because a read tells you what was true a moment ago and nothing holds between the read and the write. Both reach Stripe. The unique constraint then rejects one INSERT, so the database ends up clean while the customer has paid twice. The constraint protected the records instead of the money.

PayRail reverses the order:

```
INSERT the row as PENDING     unique constraint decides the winner here
call stripe                   only the winner gets this far
UPDATE to SUCCEEDED           and write the ledger pair
```

The loser of the race catches the constraint violation, reads the winning row, and returns it. Both callers get the same payment back, which is what idempotent means from the outside. Postgres arbitrates, and it does so before anything irreversible happens.

The Stripe call sits outside any transaction, so a slow network call never holds a database connection open.

## The ledger

Payments write two rows rather than a status flag. Money leaves one account and the same amount arrives in another:

```
payment_id   account      amount   direction
1            cust_001     5000     DEBIT
1            merch_001    5000     CREDIT
```

Across the whole table the debits and credits have to cancel. If they ever do not, money was invented or destroyed somewhere, and a status column would not have told you. Balances are derived from these rows rather than stored and hoped for. Entries are append only, so a refund writes a reversing pair instead of editing history.

## Stack

Java 17, Spring Boot 3.2, PostgreSQL 16, Stripe API, Docker Compose, JUnit 5 with Mockito.

## Running it

You need Docker, Maven, and a Stripe test key.

```bash
echo 'STRIPE_API_KEY=sk_test_your_key' > .env
set -a; source .env; set +a

docker compose up -d
mvn spring-boot:run
```

Then create a payment:

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -d '{"customerId":"cust_001","merchantId":"merch_001","amountCents":5000,"currency":"USD"}'
```

Send it again with the same key and you get the same payment back, with no second charge on Stripe.

To see the race handling, fire twenty at once:

```bash
seq 1 20 | xargs -P 20 -I{} curl -s -o /dev/null -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: race-001" \
  -d '{"customerId":"cust_001","merchantId":"merch_001","amountCents":2500,"currency":"USD"}'
```

One payment row, one Stripe charge, two ledger entries.

## Tests

`docker compose up -d` first, since the suite runs against a real Postgres. The unique constraint is the thing under test, so an in memory database would not prove anything.

```bash
mvn test
```

Five integration tests cover the same key returning the same payment, twenty concurrent requests producing a single charge, the ledger balancing after a payment, an unknown account being rejected before anything is written, and a Stripe failure leaving the payment FAILED with no ledger entries.

Stripe is mocked so the tests never hit the real API, and so the number of charge attempts can be counted.

## Design notes

Requests and responses are separate records rather than the JPA entity. The entity is shaped by the database, and returning it directly would leak internal row ids and turn a column rename into a breaking API change.

The API takes external account handles like `cust_001` rather than internal primary keys, so the accounts table can be renumbered without touching any client.

Validation runs before the controller body, so a negative amount is rejected without reaching Stripe or the ledger.

The idempotency key is also passed through to Stripe, which gives two independent guards. The database constraint stops a second call being made, and Stripe's own key stops a second call counting if a retry slips through.

## Not done yet

Webhook handling is not built, so payment state is only updated inline. A reconciliation job would be the natural companion, comparing local records against Stripe and settling rows that got stuck.

If the process dies between the Stripe call and the status update, a row stays on PENDING while Stripe has taken the money. Recovering those is the reconciliation job's work.

There is no CI workflow yet, and validation failures return Spring's default error body rather than a tidied one.

## Notes on how this was built

AI assistance was used for scaffolding and for a first pass at most files. Every design decision, the idempotency ordering in particular, was reviewed and reasoned throughly by me ***(see my heavily annotated notes!!!)*** rather than accepted as written. The commit history is deliberately granular so the sequence is reviewable.

An earlier version of the payment service had the broken ordering described at the top. It was caught in review and fixed, and the concurrency test exists so it cannot come back.
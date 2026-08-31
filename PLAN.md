# LedgerFlow: Distributed Payment Processing Platform

## Problem Statement

Building robust payment infrastructure requires solving several hard problems:
- **Idempotency**: Retried requests must never duplicate charges
- **Consistency**: Payment state must remain consistent under failures and concurrent webhooks
- **Auditability**: Double-entry ledger ensures financial transactions are traceable and balanced
- **Resilience**: Failed payment processing must retry intelligently and surface unrecoverable failures

This project demonstrates solutions to these production payment system challenges.

## Technical Goals

### Core MVP (24h target)
1. **Idempotent Payment API**
   - Accept payment requests with idempotency keys
   - Guarantee exactly-once processing even with duplicate requests
   - Return consistent results for repeated calls

2. **Double-Entry Ledger**
   - Model payments as accounting transactions (credits/debits)
   - Enforce ledger invariant: total debits == total credits
   - Enable financial audit trail

3. **Webhook Processing**
   - Receive and verify Stripe webhooks
   - Deduplicate webhook events (handle re-deliveries)
   - Update payment state asynchronously

4. **Fault Tolerance**
   - Retry failed payments with exponential backoff
   - Demonstrate recovery from partial failures
   - Handle concurrent requests safely

5. **Testing & Validation**
   - Integration tests covering happy paths and failure scenarios
   - Demonstrate idempotency guarantees through test cases
   - Docker setup for reproducible local testing

### Future Extensions
- Redis-based caching and rate limiting
- Kafka event stream for scalable event processing
- Automated reconciliation service
- Observability and load testing

## Architecture Overview

```
React/TypeScript Frontend (optional for MVP)
       ↓
Java Spring Boot API Server
       ↓
   PostgreSQL
(accounts, transactions, ledger_entries)
       ↓
Webhook Processor (async)
       ↓
Stripe Sandbox API
```

## Milestones

### Milestone 1: Project Setup & Database (2-3h)
- [ ] Spring Boot project scaffolding
- [ ] PostgreSQL schema design
- [ ] Docker Compose setup for local development

### Milestone 2: Core Payment API (4-5h)
- [ ] Idempotent payment request handler
- [ ] Ledger transaction creation
- [ ] Stripe integration
- [ ] Basic error handling

### Milestone 3: Webhook Processing (3-4h)
- [ ] Webhook receiver and signature verification
- [ ] Event deduplication logic
- [ ] Payment state updates

### Milestone 4: Testing & Documentation (3-4h)
- [ ] Integration tests (idempotency, webhook dedup, failures)
- [ ] Demo script showing failure scenarios
- [ ] README with setup and usage instructions

## Key Technical Decisions

### Why Spring Boot?
- Standard JVM ecosystem for backend services
- Strong foundation for REST APIs and database operations
- Clear patterns for transaction management and testing

### Why PostgreSQL?
- ACID guarantees necessary for financial data
- Strong support for transactions and data integrity
- Suitable for ledger-based accounting models

### MVP Scope: What We're Skipping (for Now)
- React dashboard (CLI/Postman testing instead)
- Kafka event stream (would use simple polling initially)
- Redis caching (add if performance requires it)
- Rate limiting (can add later)
- Advanced observability (logging sufficient for MVP)

## Success Criteria

The project will be considered MVP-complete when:
1. Idempotent API correctly handles duplicate requests (demonstrated by test)
2. Webhooks deduplicate correctly even when replayed 10+ times
3. Ledger invariant holds: all debits equal all credits
4. Integration tests cover core failure scenarios
5. Local deployment works reliably with Docker Compose
6. README includes runnable demo of idempotency and webhook handling

## Development Approach

- Use AI tools for scaffolding and boilerplate
- Validate all generated code through tests and manual review
- Keep commits small and focused
- Document tricky business logic inline

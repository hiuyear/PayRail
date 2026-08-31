# LedgerFlow: Distributed Payment Processing Platform

A production-grade payment processing system demonstrating idempotency, double-entry ledger accounting, webhook deduplication, and fault tolerance.

## Overview

LedgerFlow solves real-world payment infrastructure challenges:

- **Idempotent Payments**: Duplicate requests never result in multiple charges
- **Double-Entry Ledger**: All transactions tracked with accounting-style debits/credits
- **Webhook Reliability**: Handles duplicate webhook deliveries from payment processors
- **Fault Tolerance**: Graceful degradation under failures with exponential backoff retries
- **Auditability**: Complete financial audit trail for compliance and debugging

## Architecture

```
Spring Boot REST API (Java 17)
       ↓
PostgreSQL (ACID ledger)
       ↓
Stripe API (Sandbox)
```

## Technology Stack

- **Backend**: Java 17, Spring Boot 3.2
- **Database**: PostgreSQL 16
- **Payment Provider**: Stripe API (Sandbox)
- **Testing**: JUnit 5, Spring Test, Testcontainers
- **DevOps**: Docker, Docker Compose

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### Setup

1. **Start PostgreSQL**
   ```bash
   docker-compose up -d
   ```

2. **Build the project**
   ```bash
   mvn clean package
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   The API will be available at `http://localhost:8080`

4. **Verify it's running**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

## API Endpoints (MVP)

### Create a Payment

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "customer_id": "cust_001",
    "merchant_id": "merch_001",
    "amount_cents": 5000,
    "currency": "USD"
  }'
```

Response:
```json
{
  "payment_id": "pay_abc123",
  "status": "SUCCEEDED",
  "amount_cents": 5000,
  "stripe_payment_intent_id": "pi_...",
  "created_at": "2026-08-30T..."
}
```

### Webhook Handler

```bash
POST http://localhost:8080/api/webhooks/stripe
```

Stripe sends payment events here. The system:
1. Verifies webhook signature
2. Deduplicates using event ID
3. Updates payment state asynchronously

## Development Approach

This project uses AI-assisted development with human validation:

- **AI-Generated Code**: Scaffolding, boilerplate, and test templates generated with AI assistance
- **Human Review**: All code reviewed for correctness, security, and alignment with design
- **Validation**: Generated code validated through integration tests and manual testing
- **Transparency**: Commits include notes on review and validation

This approach prioritizes code quality while leveraging AI for efficiency.

## Testing

Run all tests:
```bash
mvn test
```

Key test scenarios:
- Idempotent payment creation (duplicate requests → same result)
- Webhook deduplication (replayed events → no duplicates)
- Ledger invariant validation (debits == credits)
- Concurrent payment processing

## Project Structure

```
├── pom.xml                      # Maven configuration
├── docker-compose.yml           # Local PostgreSQL setup
├── PLAN.md                      # Technical planning document
├── README.md                    # This file
├── src/main/
│   ├── java/com/ledgerflow/
│   │   ├── Application.java     # Spring Boot entry point
│   │   ├── api/                 # REST controllers
│   │   ├── domain/              # Entity models
│   │   ├── service/             # Business logic
│   │   ├── repository/          # Data access
│   │   └── exception/           # Custom exceptions
│   └── resources/
│       ├── application.yml      # Spring configuration
│       └── schema.sql           # Database schema
└── src/test/
    └── java/com/ledgerflow/     # Integration tests
```

## Next Steps

- [ ] Implement payment API endpoints
- [ ] Add webhook processing
- [ ] Build integration tests
- [ ] Load testing and performance optimization
- [ ] Observability and monitoring dashboards

## License

MIT

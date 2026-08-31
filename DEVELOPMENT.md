# Development Workflow: AI-Assisted with Human Validation

This document describes how we use AI tools to accelerate development while maintaining code quality through human review and validation.

## Philosophy

- **Generate**: Use AI to quickly produce scaffolding, boilerplate, and templates
- **Review**: Critically examine generated code for correctness, style, and design
- **Validate**: Test all code before committing
- **Document**: Explain what was changed and why

This approach is transparent and matches modern development practices, especially valued by companies building production systems.

## Workflow for Each Feature

### 1. Create a Feature Branch

```bash
git checkout -b feat/payment-api
```

Branch naming:
- `feat/` — new features
- `fix/` — bug fixes  
- `refactor/` — code cleanup
- `test/` — adding tests

### 2. Generate Code with AI

Use Claude Code or similar to generate:
- REST controller stubs
- Entity models
- Service implementations
- Test templates

Example prompt:
```
Generate a Spring Boot REST controller for creating payments.
Requirements:
- Accept idempotency-key header
- Return consistent results for duplicate requests
- Validate input before processing
```

### 3. Review Generated Code

**Critical questions to ask:**
- Does this handle all error cases?
- Are there security issues (SQL injection, XSS)?
- Does this follow Spring Boot patterns?
- Are there concurrency issues?
- Is the code testable?

**If issues found**: Ask AI to revise, or fix manually. Don't commit untested code.

### 4. Write or Update Tests

For each feature, validate:
- Happy path (normal operation)
- Error cases (invalid input, failures)
- Concurrency (duplicate requests)
- Edge cases (boundary values)

Example test:

```java
@Test
void testPaymentIdempotency() {
    PaymentRequest req = new PaymentRequest("cust_001", "merch_001", 5000);
    String idempotencyKey = "unique-123";
    
    // First request
    PaymentResponse response1 = paymentService.createPayment(req, idempotencyKey);
    
    // Second request with same key
    PaymentResponse response2 = paymentService.createPayment(req, idempotencyKey);
    
    // Should get identical results
    assertEquals(response1.getPaymentId(), response2.getPaymentId());
    assertEquals(response1.getStatus(), response2.getStatus());
    
    // Verify only one Stripe charge was created
    assertEquals(1, stripePaymentIntents.size());
}
```

### 5. Run Tests Locally

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=PaymentServiceTest

# Run with coverage
mvn test jacoco:report
```

**Rule**: Don't commit unless all tests pass.

### 6. Create a Commit with Clear Message

Format:

```
[feat] Implement idempotent payment API

This implements the core payment creation endpoint with idempotency
guarantees using idempotency keys stored in the database.

What was generated:
- PaymentController with /api/payments POST endpoint
- PaymentService for business logic
- PaymentRepository for database access

What was reviewed/validated:
- Verified idempotency logic handles duplicate requests correctly
- Added test covering duplicate request scenario
- Verified Stripe API integration
- Confirmed ledger entries created correctly

Generated code was reviewed for:
- Error handling (invalid amounts, missing fields)
- Security (no SQL injection, proper input validation)
- Spring Boot patterns (proper annotations, dependency injection)
- Database transactions (atomicity)

Co-Authored-By: Claude <ai-assistant>
```

Key elements:
- `[feat/fix/test]` tag for quick scanning
- What was generated
- What was reviewed/validated
- Why the choices made sense

### 7. Prepare for Review (GitHub PR)

When you're ready to push:

```bash
git push origin feat/payment-api
```

Then create a PR on GitHub with:

**Title**: Concise description of change
```
Implement idempotent payment API with Stripe integration
```

**Description**:
```markdown
## What does this do?
Implements core payment creation endpoint with exactly-once semantics.

## How does it work?
- Accepts payment requests with idempotency keys
- Stores idempotency keys + results in database
- Returns cached result for duplicate requests
- Creates Stripe PaymentIntent and ledger entries

## Testing
- ✅ Idempotency tests (duplicate requests)
- ✅ Stripe integration tests
- ✅ Ledger invariant tests
- ✅ Error handling tests

## Development Notes
- Used AI for controller scaffolding and service template
- Reviewed generated code for security, error handling
- Validated through comprehensive integration tests
- All tests passing locally
```

## Example: Workflow in Action

### Step 1: Feature branch
```bash
git checkout -b feat/webhook-processing
```

### Step 2: Generate code
*Ask AI to generate:*
- WebhookController to receive Stripe webhooks
- WebhookVerifier to verify signatures
- WebhookProcessor to handle events
- WebhookDeduplicator to prevent duplicate processing

### Step 3: Review & fix
*Review generated code:*
- ✅ Signature verification looks correct
- ⚠️ WebhookDeduplicator uses simple HashMap (not thread-safe)
  - Fix: Use `ConcurrentHashMap` or database-backed solution
- ✅ Event processing is async (good)
- ⚠️ Missing dead-letter queue for failed events
  - Add to TODO for future work

### Step 4: Write tests
```java
@Test
void testWebhookDeduplication() {
    // Send same webhook event 5 times
    String eventId = "evt_123";
    for (int i = 0; i < 5; i++) {
        webhookController.handle(createWebhookEvent(eventId));
    }
    
    // Should only process once
    assertEquals(1, processedEvents.size());
    assertTrue(processedEvents.contains(eventId));
}

@Test
void testInvalidSignature() {
    // Send webhook with invalid signature
    assertThrows(InvalidSignatureException.class, () -> {
        webhookController.handle(webhookWithBadSignature());
    });
}
```

### Step 5: Commit
```bash
git commit -m "[feat] Implement Stripe webhook processing with deduplication

Adds webhook receiver that:
- Verifies Stripe signatures
- Deduplicates events using event ID
- Updates payment state asynchronously

Generated: WebhookController, WebhookVerifier
Reviewed: Signature verification, thread safety
Validated: Deduplication tests, invalid signature handling

All tests passing.

Co-Authored-By: Claude <ai-assistant>"
```

### Step 6: Push & PR
```bash
git push origin feat/webhook-processing
```

Create PR on GitHub describing the change.

## Best Practices

### Do
- ✅ Always run `mvn test` before committing
- ✅ Test edge cases, not just happy paths
- ✅ Document *why* in commit messages, not just *what*
- ✅ Ask AI for revisions if generated code seems wrong
- ✅ Keep commits focused on one feature/fix
- ✅ Review error messages and exception handling

### Don't
- ❌ Commit without running tests
- ❌ Copy-paste generated code without understanding it
- ❌ Skip security review (input validation, injection risks)
- ❌ Use generated code that seems overcomplicated
- ❌ Ignore compiler warnings
- ❌ Mix refactoring with feature work in same commit

## Tools

- **Code Generation**: Claude Code, GitHub Copilot
- **Testing**: JUnit 5, Spring Test, Testcontainers
- **Build**: Maven
- **Version Control**: Git

## Questions?

If you're unsure:
1. **Is this code correct?** → Write a test to verify
2. **Is this production-ready?** → Would it handle failures gracefully?
3. **Did AI generate good code?** → Can you understand it and explain it to someone else?

If the answer to any of these is "no," refactor or rewrite before committing.

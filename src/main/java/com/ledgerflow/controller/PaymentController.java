package com.ledgerflow.controller;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.dto.PaymentRequest;
import com.ledgerflow.dto.PaymentResponse;
import com.ledgerflow.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    // constructor: spring injects PaymentService
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /api/payments
     *
     * creates a new payment with idempotency protection.
     *
     * @param request payment details (customer_id, merchant_id, amount_cents, currency)
     * @param idempotencyKey unique identifier for this payment (from Idempotency-Key header)
     * @return 200 OK with PaymentResponse JSON
     *
     * flow:
     * 1. spring parses incoming JSON into PaymentRequest object
     * 2. spring extracts Idempotency-Key header
     * 3. THIS METHOD receives both as parameters
     * 4. THIS METHOD calls paymentService.createPayment() — service does all the work
     * 5. service returns Payment object (already saved to database, charged on stripe, etc)
     * 6. THIS METHOD converts Payment to PaymentResponse (API format)
     * 7. THIS METHOD returns HTTP 200 response
     *
     * KEY PATTERN: controller only handles HTTP. all business logic is in the service.
     * if you needed to call this logic from a background job, you'd just call the service directly (no HTTP)
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
        // @Valid is the on-switch. without it the annotations on PaymentRequest never run
        // and -5000 goes straight through to stripe and the ledger.
        @Valid @RequestBody PaymentRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        // STEP 1: call the service to process the payment (all business logic happens here)
        Payment payment = paymentService.createPayment(request, idempotencyKey);

        // STEP 2: convert Payment entity to PaymentResponse DTO (for API response)
        PaymentResponse response = PaymentResponse.fromPayment(payment);

        // STEP 3: return HTTP 200 OK with JSON response
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/payments/{paymentId}
     *
     * retrieves a payment by its ID.
     *
     * @param paymentId the payment ID (e.g., "pay_abc123")
     * @return 200 OK with payment details, or 404 if not found
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        return paymentService.getPaymentById(paymentId)
            .map(payment -> ResponseEntity.ok(PaymentResponse.fromPayment(payment)))
            .orElse(ResponseEntity.notFound().build());
    }
}

/**
 * paymentController: handles ALL HTTP requests for payment operations.
 *
 * structure of this class:
 * - ONE class: PaymentController handles all payment HTTP endpoints
 * - @RestController: tells spring "this class handles HTTP"
 * - @RequestMapping("/api/payments"): base URL for all methods in this class
 * - CONSTRUCTOR: injects PaymentService dependency (the actual business logic)
 * - ONE METHOD per HTTP operation:
 *   - @PostMapping: POST /api/payments (create payment)
 *   - @GetMapping("/{id}"): GET /api/payments/{id} (retrieve payment)
 *   - (could add @PutMapping, @DeleteMapping, etc for other operations)
 * - EACH METHOD: calls the corresponding PaymentService method (so technically the method here is just a wrapper foreach service)
 *
 * what this does (overall):
 * - receives HTTP requests from the frontend
 * - extracts data (body, headers, URL parameters)
 * - calls PaymentService methods (all business logic happens in the service)
 * - formats responses as JSON
 * - sends back HTTP responses
 *
 * why it exists (separation of concerns):
 * - controller ONLY handles HTTP plumbing (parse request, format response)
 * - service ONLY handles business logic (idempotency, Stripe calls, ledger)
 * - if you need payment logic from a background job, just call service directly (no HTTP needed)
 *
 * example: flow for POST /api/payments:
 * 1. client sends: POST /api/payments with JSON body { customer_id, merchant_id, amount }
 * 2. client includes header: Idempotency-Key: abc123
 * 3. spring routes to createPayment() method in this class
 * 4. spring auto-parses JSON → PaymentRequest object
 * 5. spring extracts header → idempotencyKey parameter
 * 6. createPayment() method calls: paymentService.createPayment(request, idempotencyKey)
 * 7. service does all the work (check idempotency, call Stripe, create ledger, save to DB)
 * 8. createPayment() method receives Payment object back from service
 * 9. createPayment() method converts Payment → PaymentResponse (API format)
 * 10. createPayment() method returns: ResponseEntity.ok(response)
 * 11. spring converts to JSON and sends HTTP 200 response to client
 */
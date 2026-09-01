package com.ledgerflow.service;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.dto.PaymentRequest;
import com.ledgerflow.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * this file (PaymentSErvice) orchestrates payment processing business logic
 * containing 3 components - attributes, main method createPayment(), and helpers 
 * in Spring boot, service is the orchestrator taht uses tools (domain, repo, other services) to implement business logic
 *
 * what this does:
 * - checks idempotency: if this payment was already processed, return cached result
 * - calls stripe api: charges the customer's credit card
 * - creates ledger entries: records debits (customer) and credits (merchant)
 * - ensures atomicity: all-or-nothing transaction (everything succeeds or everything rolls back)
 *
 * why it's separate from the repository:
 * - repository just talks to the database
 * - service contains the decision logic (what to do with the data)
 * - this separation means you can test the logic without HTTP or database
 *
 * @Transactional ensures all database operations happen together:
 * - if anything fails, the entire transaction rolls back (no partial updates)
 * - guarantees consistency: ledger is always balanced
 *
 * flow for createPayment():
 * 1. check if idempotency_key already exists in database
 *    - if yes, return the cached payment (duplicate protection, we dont wanna charge customer twice)
 *    - if no, continue to step 2
 * 2. call stripe to charge the customer
 * 3. create payment record in database
 * 4. create ledger entries (debit customer account, credit merchant account)
 * 5. save everything to database in one transaction
 * 6. return the payment
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeService stripeService;
    private final LedgerService ledgerService;

    // constructor: SPRING DEPENDENCY INJECTION!!!
    public PaymentService(
        PaymentRepository paymentRepository,
        StripeService stripeService,
        LedgerService ledgerService
    ) {
        this.paymentRepository = paymentRepository;
        this.stripeService = stripeService;
        this.ledgerService = ledgerService;
    }

    /**
     * create a payment with idempotency guarantee.
     * @param request payment details (customer_id, merchant_id, amount, etc)
     * @param idempotencyKey unique identifier for this payment attempt (must be unique per payment)
     * @return the payment record (newly created or cached if duplicate)
     */
    @Transactional
    public Payment createPayment(PaymentRequest request, String idempotencyKey) {
        // step 1: check idempotency,is this payment already processed?
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            // payment already processed, return cached result
            // this protects against duplicate charges if the request is retried
            return existing.get();
        }

        // step 2: create a new payment record
        Payment payment = new Payment();
        payment.setPaymentId("pay_" + UUID.randomUUID().toString()); // UUID generates unique id for this payment
        payment.setCustomerAccountId(request.getCustomerAccountId());
        payment.setMerchantAccountId(request.getMerchantAccountId());
        payment.setAmountCents(request.getAmountCents());
        payment.setCurrency(request.getCurrency());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus("PENDING");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        // step 3: call stripe to actually charge the customer
        String stripePaymentIntentId = stripeService.createPaymentIntent(
            request.getAmountCents(),
            request.getCustomerAccountId()
        );
        payment.setStripePaymentIntentId(stripePaymentIntentId);
        payment.setStatus("SUCCEEDED");
        payment.setProcessedAt(LocalDateTime.now());

        // step 4: save payment to database
        // at this point, the payment is recorded in our system
        Payment savedPayment = paymentRepository.save(payment);

        // step 5: create ledger entries (accounting records)
        // double-entry bookkeeping: debit customer, credit merchant
        ledgerService.createLedgerEntries(savedPayment);

        // step 6: return the payment
        // if we get here, everything succeeded. if anything failed above, @Transactional rolls back everything
        return savedPayment;
    }

    //used to check if a payment was already processed
    public Optional<Payment> getPaymentByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey);
    }

    public Optional<Payment> getPaymentById(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId);
    }

    /**
     * get payment by stripe payment intent id.
     * used when processing stripe webhooks to find which payment this event is for.
     */
    public Optional<Payment> getPaymentByStripeId(String stripePaymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId);
    }
}

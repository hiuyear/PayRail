package com.ledgerflow.repository;

import com.ledgerflow.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * paymentRepository: data access layer for payment objects.
 *
 * what this does:
 * provides methods to save, find, and delete payment records from the database
 * an interface (contract), not a class — spring implements it automatically
 *
 * how spring data jpa magic works:
 * - extends JpaRepository<Payment, Long> gives you free methods:
 *   - save(payment p) — insert or update into database
 *   - findById(Long id) — select by primary key
 *   - findAll() — select all rows
 *   - delete(payment p) — delete from database
 * - you write zero sql. spring generates it from the method names.
 *
 * custom query methods (we define these):
 * - findByIdempotencyKey(String key) — spring generates: select * from payments where idempotency_key = ?
 * - findByStripePaymentIntentId(String id) — spring generates: select * from payments where stripe_payment_intent_id = ?
 * - findByPaymentId(String id) — spring generates: select * from payments where payment_id = ?
 *
 * Optional<Payment> means: the payment might exist or might not exist
 * - if found: Optional.isPresent() = true, use .get() to retrieve
 * - if not found: Optional.isPresent() = false, don't call .get()
 *
 * example usage (in PaymentService):
 * Optional<Payment> existing = paymentRepository.findByIdempotencyKey("abc123");
 * if (existing.isPresent()) {
 *     return existing.get();  // payment already processed, return cached result
 * }
 * // payment doesn't exist, create new one...
 * here we check if the payment instance already exists by checking idempot key (hence cbeck if already paid), if yes,
 * we just directly use the already-stored payment instance, if not, create new instance
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Find a payment by its idempotency key.
     * Returns Optional because the payment might not exist.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find a payment by its Stripe PaymentIntent ID.
     * Used when processing Stripe webhooks.
     */
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Find a payment by its public-facing payment ID.
     */
    Optional<Payment> findByPaymentId(String paymentId);
}

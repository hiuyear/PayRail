package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// what this file does: defines the class Payment, whcih includes:
// 1. each @column field: an attribute of each payment object, "i expect a column in the database"
// 2. constructors and getters (basic java stuff)
// 3. class methods to retrieve specific attributes of each payment instance
// note: nullable = false means it must be provided, unique means each value in the column must be unique, uddatable = false means it must be fixed)
// this creates maps to the payments table in PostgreSQL; this is the java representation of the table
// schema.sql already created the database table. Spring uses the @Column annotations to know how to convert Java objects ↔ SQL rows. It verifies the Java class matches the database table

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId;

    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    @Column(name = "merchant_account_id", nullable = false)
    private Long merchantAccountId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency")
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Constructor (no-arg required by JPA)
    public Payment() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public Long getCustomerAccountId() {
        return customerAccountId;
    }

    public void setCustomerAccountId(Long customerAccountId) {
        this.customerAccountId = customerAccountId;
    }

    public Long getMerchantAccountId() {
        return merchantAccountId;
    }

    public void setMerchantAccountId(Long merchantAccountId) {
        this.merchantAccountId = merchantAccountId;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}

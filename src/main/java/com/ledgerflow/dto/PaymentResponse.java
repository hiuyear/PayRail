package com.ledgerflow.dto;

import com.ledgerflow.domain.Payment;

import java.time.LocalDateTime;

public record PaymentResponse(
    String paymentId,
    String status,
    Long amountCents,
    String currency,
    String stripePaymentIntentId,
    LocalDateTime createdAt,
    LocalDateTime processedAt
) {
    public static PaymentResponse fromPayment(Payment payment) {
        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getStatus(),
            payment.getAmountCents(),
            payment.getCurrency(),
            payment.getStripePaymentIntentId(),
            payment.getCreatedAt(),
            payment.getProcessedAt()
        );
    }
}

// payment response = what goes out. payment request = what comes in.
//
// basically filters the payment object before sending it back to the customer.
// you dont wanna send the whole payment object back!
//
// returning Payment directly would leak "id": 1 (our actual db row number) and the
// idempotency key, which is internal bookkeeping. once a client can see those they start
// depending on them and we can never renumber the table.
//
// other reason: if we rename the column amount_cents later, returning Payment means the
// json key changes too and every app calling us breaks. with a dto we just change one
// line in fromPayment() and the json stays the same.
//
// fromPayment() is the one place that turns a db row into a response.

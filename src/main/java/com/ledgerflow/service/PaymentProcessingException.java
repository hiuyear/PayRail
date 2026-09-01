package com.ledgerflow.service;

public class PaymentProcessingException extends RuntimeException {

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentProcessingException(String message) {
        super(message);
    }
}

// our own exception for "the charge failed", so the rest of the code never has to import
// stripe classes just to handle a failure.
//
// extends RuntimeException (unchecked) on purpose. a checked exception would force every
// method in between to declare throws, which is just noise. we handle it in one place.

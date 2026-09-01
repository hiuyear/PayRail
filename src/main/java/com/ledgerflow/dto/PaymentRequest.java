package com.ledgerflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(

    @NotBlank(message = "customerId is required")
    String customerId,

    @NotBlank(message = "merchantId is required")
    String merchantId,

    @NotNull(message = "amountCents is required")
    @Positive(message = "amountCents must be greater than zero")
    @Max(value = 100_000_000L, message = "amountCents exceeds the per-payment limit")
    Long amountCents,

    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code, e.g. USD")
    String currency
) {

    /** currency is optional in the request body */
    public String currencyOrDefault() {
        return (currency == null || currency.isBlank()) ? "USD" : currency;
    }
}

// payment request = what comes in. payment response = what goes out.
//
// this is a stranger's input, anyone can post whatever they want here. the annotations
// decide what we refuse. they run before the controller body, so bad input never reaches
// stripe or the ledger, it just gets a 400.
// 
// basically paymentRequest valdiates the customer's request based on the request they're sending in, 
// only if it's valid then do you do the whole service stuff on the ledger and payemnet systems
//
// the annotations are off until @Valid is added on the controller parameter,
// without it -5000 sails straight through and the code only looks validated
//
// no status field here on purpose. if someone posts "status": "SUCCEEDED" spring
// throws it away since there's nowhere to put it, so they can't mark their own payment paid.
//
// PaymentRequest is only the first gate/defense, only check things that don't need outside knwoeldge.
// this only checks well-formed stuff (is it a positive number). it can't check
// if account cust_999 actually exists, that needs a db query, so the service does it later

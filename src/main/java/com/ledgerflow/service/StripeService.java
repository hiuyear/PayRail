package com.ledgerflow.service;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final StripeClient stripeClient;

    public StripeService(@Value("${stripe.api-key}") String apiKey) {
        this.stripeClient = new StripeClient(apiKey);
    }

    public String createPaymentIntent(long amountCents, String currency, String idempotencyKey) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountCents)
            .setCurrency(currency.toLowerCase())
            .setPaymentMethod("pm_card_visa")
            .setConfirm(true)
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build();

        try {
            PaymentIntent intent = stripeClient.paymentIntents().create(params, options);
            log.info("stripe charged: id={} status={}", intent.getId(), intent.getStatus());
            return intent.getId();
        } catch (StripeException e) {
            log.warn("stripe failed for key={}: {}", idempotencyKey, e.getMessage());
            throw new PaymentProcessingException("stripe rejected the charge: " + e.getMessage(), e);
        }
    }
}

// the only file that knows stripe exists. everything else calls this, so swapping or
// faking the processor is a one file change.
//
// same client object pattern as openai: build the client once with your key, then call
// methods on it. built once at startup, not per request, since it holds a connection pool.
//
// the api key comes from the STRIPE_API_KEY env var (see application.yml) so a real key
// never gets committed.
//
// pm_card_visa is stripe's test card token that always succeeds, so the demo runs without
// a real card. a real integration would take a payment method id from the frontend.
//
// we pass our idempotency key to stripe too. that's a second layer: our db constraint stops
// us calling stripe twice, and this stops stripe counting it twice if our retry does get through.
//
// stripe throws a checked StripeException. we catch it and rethrow our own unchecked one so
// callers don't need to import stripe types.

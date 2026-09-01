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
            .setAmount(amountCents) // 5000 = $50.00, always in cents
            .setCurrency(currency.toLowerCase()) // stripe wants "usd" not "USD"
            .setPaymentMethod("pm_card_visa") // test card that always succeeds
            .setConfirm(true)  // charge it now, don't just create it
            // your stripe dashboard has redirect based methods enabled (klarna, ideal etc).
            // those need a return_url to send the customer back to. we have no browser here,
            // so tell stripe never to pick one.
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .setAllowRedirects(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                    .build()
            )
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build();

        try {
            PaymentIntent intent = stripeClient.paymentIntents().create(params, options);
            // paymentIntent = stripe's object for one attempt to collect money. stripe's, not ours.
            // paymentIntents() is the grouping of payment intent operations, .create() is the call.
            // takes two args: params (what to charge) and options (how to send it).

            log.info("stripe charged: id={} status={}", intent.getId(), intent.getStatus());
            return intent.getId();
        } catch (StripeException e) {
            log.warn("stripe failed for key={}: {}", idempotencyKey, e.getMessage());
            throw new PaymentProcessingException("stripe rejected the charge: " + e.getMessage(), e);
        }
    }
}

// this file, one job: take an amount, charge a card, hand back an id
// what a PaymentIntent is: Stripe's object for one attempt to collect money
// Not "a charge" exactly, but it's the whole lifecycle: created → confirmed → succeeded (or failed, or needs the customer to do 3D Secure).
// the only file that knows stripe exists. everything else calls this
//
// same client object pattern as openai: build the client once with your key, then call
// methods on it. built once at startup, not per request
//
// pm_card_visa is stripe's test card token that always succeeds, so the demo runs without
// a real card. a real integration would take a payment method id from the frontend.
//
// we pass our idempotency key to stripe too. that's a second layer: our db constraint stops
// us calling stripe twice, and this stops stripe counting it twice if our retry does get through.
//
// stripe throws a checked StripeException. we catch it and rethrow our own unchecked one so
// callers don't need to import stripe types. (a CHECKED exception, hence forcing every method up the call chain to write throws StripeException)

package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.dto.PaymentRequest;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public static final String PENDING = "PENDING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final StripeService stripeService;
    private final LedgerService ledgerService;
    private final TransactionTemplate tx;

    public PaymentService(
        PaymentRepository paymentRepository,
        AccountRepository accountRepository,
        StripeService stripeService,
        LedgerService ledgerService,
        PlatformTransactionManager transactionManager
    ) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.stripeService = stripeService;
        this.ledgerService = ledgerService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Payment createPayment(PaymentRequest request, String idempotencyKey) {

        // fast path. a plain retry minutes later never gets past here.
        Optional<Payment> alreadyDone = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (alreadyDone.isPresent()) {
            return alreadyDone.get();
        }

        // "cust_001" -> account row. 404 if it doesn't exist.
        Account customer = requireAccount(request.customerId());
        Account merchant = requireAccount(request.merchantId());

        // PHASE 1: claim the key before spending any money.
        // the INSERT row  now happens before Stripe, so the unique index picks the winner while zero money has moved
        Payment payment;
        try {
            payment = tx.execute(status -> insertPending(request, idempotencyKey, customer, merchant));
        } catch (DataIntegrityViolationException race) {
            // we lost. someone else inserted this key first, so return their row.
            log.info("duplicate request for key={}, returning the winner's payment", idempotencyKey);
            return tx.execute(status -> paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                    "unique violation on " + idempotencyKey + " but no row found")));
        }

        // PHASE 2: charge. no transaction open, no db connection held.
        String stripeId;
        try {
            stripeId = stripeService.createPaymentIntent(
                payment.getAmountCents(),
                payment.getCurrency(),
                idempotencyKey
            );
        } catch (RuntimeException e) {
            tx.execute(status -> markFailed(payment.getId(), e.getMessage()));
            throw e;
        }

        // PHASE 3: record the result and write the ledger, together.
        return tx.execute(status -> markSucceeded(payment.getId(), stripeId));
    }


    // PRIVATE HELPERS
    private Payment insertPending(
        PaymentRequest request,
        String idempotencyKey,
        Account customer,
        Account merchant
    ) {
        Payment payment = new Payment();
        payment.setPaymentId("pay_" + UUID.randomUUID());
        payment.setCustomerAccountId(customer.getId());
        payment.setMerchantAccountId(merchant.getId());
        payment.setAmountCents(request.amountCents());
        payment.setCurrency(request.currencyOrDefault());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus(PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        // saveAndFlush, not save: forces the INSERT to hit postgres right now so the
        // unique violation is thrown here where we can catch it.
        return paymentRepository.saveAndFlush(payment);
    }

    private Payment markSucceeded(Long paymentId, String stripeId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStripePaymentIntentId(stripeId);
        payment.setStatus(SUCCEEDED);
        payment.setProcessedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        ledgerService.createLedgerEntries(saved);
        return saved;
    }

    private Payment markFailed(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(FAILED);
        payment.setUpdatedAt(LocalDateTime.now());
        log.warn("payment {} failed: {}", payment.getPaymentId(), reason);
        return paymentRepository.save(payment);
    }

    private Account requireAccount(String externalId) {
        return accountRepository.findByExternalId(externalId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "unknown account: " + externalId));
    }

    public Optional<Payment> getPaymentByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey);
    }

    public Optional<Payment> getPaymentById(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId);
    }

    public Optional<Payment> getPaymentByStripeId(String stripePaymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId);
    }
}

// THE BUG THIS FILE FIXES
//
// the old order was: SELECT (does this key exist?) -> charge stripe -> INSERT.
// two requests with the same key both pass the SELECT, both charge, and only then does
// the unique constraint reject one INSERT. database ends up clean, customer paid twice.
// the constraint protected the records instead of the money.
//
// a SELECT is not a lock. it tells you what was true a microsecond ago, and anything can
// happen in the gap before your write. if (!exists) { create(); } is broken under
// concurrency, always.
//
// the fix is just the order: INSERT first, charge second. now the unique index decides the
// winner before any money moves, and postgres is genuinely atomic about that.
//
//
// THE THREE PHASES
//
// phase 1 insertPending - write the row as PENDING (using helper markpending, which basically inserts a payment object into the db directly and returns the payment obj). 
// this claims the key. the loser of a
//   race gets DataIntegrityViolationException, catches it, reads the winner's row and
//   returns that. both callers get the same payment back, which is what idempotent means. 
// BASICALLY WE ARE CATCHING DUPLICATE REQUEST ERRORS AND FINDING A WINNER BEFORE THE ACTUAL MONEY CHARGING DISASTER (ie. charging customer twice)
//DataIntegrityViolationException is Spring's wrapper.
// we already set the postgres column for each payment to be UNIQUE, 
// and since in the event of a conflict there is already a row with that key, spring wrote to the exception wrapper to hide the database driver's error.
//
// phase 2 stripe - the charge. only the winner ever reaches it.
// if we get a RuntimeException here, it's STRIPE saying that card declined or network died (real failure)
//
// phase 3 markSucceeded - flip to SUCCEEDED and write the ledger pair in one transaction.
//
// PENDING is a real state now, not dead code. it's what is in the database during the
// couple of seconds stripe is being called, and it's what a duplicate collides with.
//
//
// WHY createPayment IS NOT @Transactional ANYMORE
//
// the stripe call takes a second or two. the old code held an open transaction and a db
// connection that whole time, so under load the connection pool drains and everything
// stalls. also if stripe succeeded and the commit then failed, we'd have charged someone
// with no record of it
//
// so: three short transactions, network call in the gap between them.
//
//
// WHY TransactionTemplate INSTEAD OF @Transactional ON THE HELPERS
//
// @Transactional works by wrapping the bean in a proxy. calls from outside go through the
// proxy and get a transaction. but a method calling another method ON ITSELF never leaves
// the object, so it skips the proxy and the annotation is silently ignored. you'd think
// you had three transactions and actually have zero. TransactionTemplate starts one
// explicitly, no proxy involved.
//
//
// saveAndFlush NOT save
//
// save() can queue the INSERT until the transaction commits, which would throw the unique
// violation outside our try block. saveAndFlush sends it to postgres immediately so the
// exception lands where we can catch it.
//
//
// KNOWN GAP
//
// if the process dies between phase 2 and phase 3, the row is stuck on PENDING while
// stripe has actually taken the money. the fast path would then return a PENDING payment
// forever. fixing that is the reconciliation job: find old PENDING rows, ask stripe what
// really happened, settle them.

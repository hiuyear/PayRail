package com.ledgerflow.service;

import com.ledgerflow.domain.LedgerDirection;
import com.ledgerflow.domain.LedgerEntry;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.dto.PaymentRequest;
import com.ledgerflow.repository.LedgerEntryRepository;
import com.ledgerflow.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentServiceIntegrationTest {

    @MockBean
    private StripeService stripeService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @BeforeEach
    void reset() {
        // ledger first, it has a foreign key onto payments
        ledgerEntryRepository.deleteAll();
        paymentRepository.deleteAll();

        when(stripeService.createPaymentIntent(anyLong(), anyString(), anyString()))
            .thenReturn("pi_fake_" + UUID.randomUUID());
    }

    private PaymentRequest request(long amountCents) {
        return new PaymentRequest("cust_001", "merch_001", amountCents, "USD");
    }

    @Test
    void sameIdempotencyKeyTwice_returnsSamePayment_andChargesStripeOnce() {
        String key = "key-" + UUID.randomUUID();

        Payment first = paymentService.createPayment(request(5000), key);
        Payment second = paymentService.createPayment(request(5000), key);

        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(paymentRepository.count()).isEqualTo(1);
        verify(stripeService, times(1)).createPaymentIntent(anyLong(), anyString(), anyString());
    }

    @Test
    void twentyConcurrentRequests_sameKey_produceOnePaymentAndOneCharge() throws Exception {
        String key = "race-" + UUID.randomUUID();
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Payment>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                // every thread parks here, so they all fire on the same instant
                startGun.await();
                return paymentService.createPayment(request(2500), key);
            }));
        }

        startGun.countDown();

        List<String> returnedIds = new ArrayList<>();
        for (Future<Payment> result : results) {
            returnedIds.add(result.get(30, TimeUnit.SECONDS).getPaymentId());
        }
        pool.shutdown();

        // all twenty callers got the same payment back
        assertThat(returnedIds).hasSize(threads);
        assertThat(returnedIds).containsOnly(returnedIds.get(0));

        // and only one of them actually spent money
        assertThat(paymentRepository.count()).isEqualTo(1);
        verify(stripeService, times(1)).createPaymentIntent(anyLong(), anyString(), anyString());

        // exactly one debit/credit pair, not twenty
        Payment stored = paymentRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(ledgerEntryRepository.findByPaymentId(stored.getId())).hasSize(2);
    }

    @Test
    void successfulPayment_writesBalancedDebitAndCreditPair() {
        Payment payment = paymentService.createPayment(request(7500), "key-" + UUID.randomUUID());

        List<LedgerEntry> entries = ledgerEntryRepository.findByPaymentId(payment.getId());

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(LedgerEntry::getDirection)
            .containsExactlyInAnyOrder(LedgerDirection.DEBIT, LedgerDirection.CREDIT);
        assertThat(entries).extracting(LedgerEntry::getAmountCents)
            .containsExactly(7500L, 7500L);

        // the invariant: money was neither invented nor destroyed
        assertThat(ledgerEntryRepository.totalDebits())
            .isEqualTo(ledgerEntryRepository.totalCredits());
    }

    @Test
    void unknownAccount_isRejectedBeforeAnythingIsWritten() {
        PaymentRequest bad = new PaymentRequest("cust_999", "merch_001", 5000L, "USD");

        assertThatThrownBy(() -> paymentService.createPayment(bad, "key-" + UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unknown account: cust_999");

        assertThat(paymentRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    void stripeFailure_marksPaymentFailed_andWritesNoLedgerEntries() {
        when(stripeService.createPaymentIntent(anyLong(), anyString(), anyString()))
            .thenThrow(new PaymentProcessingException("card declined"));

        String key = "key-" + UUID.randomUUID();

        assertThatThrownBy(() -> paymentService.createPayment(request(5000), key))
            .isInstanceOf(PaymentProcessingException.class);

        Payment stored = paymentRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentService.FAILED);
        assertThat(stored.getStripePaymentIntentId()).isNull();

        // a failed charge must never touch the books
        assertThat(ledgerEntryRepository.findByPaymentId(stored.getId())).isEmpty();
    }
}

// integration tests for the idempotency guarantee.
//
// these run against a REAL postgres (the ledgerflow_test database, created by
// docker/init-test-db.sh) because the whole guarantee rests on a real unique constraint.
// an in-memory db like h2 enforces constraints differently and the race test would prove
// nothing.
//
// so you must `docker compose up -d` before `mvn test`. testcontainers would make this
// self-contained, but docker-java can't talk to docker engine 29.x yet, so this is the
// tradeoff for now.
//
// stripe is mocked. two reasons: tests must not hit the real api, and a mock can be COUNTED,
// which is how we assert "twenty requests, exactly one charge".
//
// the class is deliberately NOT @Transactional. the usual spring test trick is to wrap each
// test in a transaction and roll it back, but that breaks the concurrency test: the 20
// threads each get their own transaction and none of them could see a row the test thread
// hadn't committed. so we clean up by hand in @BeforeEach instead.
//
// the CountDownLatch is what makes the race test a real race. without it the threads start
// staggered as the pool spins up and the winner commits before the others even begin, so
// they'd all take the fast path and the DataIntegrityViolationException branch would never
// be exercised. every thread parks on startGun.await(), then one countDown() releases them
// together.

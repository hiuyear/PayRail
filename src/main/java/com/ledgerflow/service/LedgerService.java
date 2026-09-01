package com.ledgerflow.service;

import com.ledgerflow.domain.LedgerDirection;
import com.ledgerflow.domain.LedgerEntry;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public void createLedgerEntries(Payment payment) {
        LedgerEntry debit = new LedgerEntry(
            payment.getId(),
            payment.getCustomerAccountId(),
            payment.getAmountCents(),
            LedgerDirection.DEBIT
        );

        LedgerEntry credit = new LedgerEntry(
            payment.getId(),
            payment.getMerchantAccountId(),
            payment.getAmountCents(),
            LedgerDirection.CREDIT
        );

        ledgerEntryRepository.saveAll(List.of(debit, credit));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesForPayment(Long paymentId) {
        return ledgerEntryRepository.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public boolean isBalanced() {
        return ledgerEntryRepository.totalDebits() == ledgerEntryRepository.totalCredits();
    }
}

// writes and checks the double entry ledger.
//
// contains class LedgerService, attributes = the ledger entry repo, constructor,
// and the main method createLedgerEntires whcih takes in a payment object 
// and spits out two objects credit and debit (both of type LedgerEntry) using the metadata from the payment object
//
// createLedgerEntries always writes the PAIR, never one row. saveAll in one @Transactional
// means both rows land or neither does, so the ledger can never be caught half updated
//
// isBalanced() is the invariant made checkable: total debits should equal total credits
// across the whole table. we use it in tests and it's what a reconciliation job would call
//
// readOnly = true on the queries tells the db and hibernate this won't write anything,
// which skips dirty checking and lets the db plan it as a read

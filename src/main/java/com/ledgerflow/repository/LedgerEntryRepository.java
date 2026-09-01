package com.ledgerflow.repository;

import com.ledgerflow.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByPaymentId(Long paymentId);

    @Query("SELECT COALESCE(SUM(e.amountCents), 0) FROM LedgerEntry e WHERE e.direction = 'DEBIT'")
    long totalDebits();

    @Query("SELECT COALESCE(SUM(e.amountCents), 0) FROM LedgerEntry e WHERE e.direction = 'CREDIT'")
    long totalCredits();
}

// purpose: fetch a payment's entries, and total up each side so we can check the books balance
//
// findByPaymentId is generated from the method name like the payment repo ones.
//
// totalDebits and totalCredits need @Query because summing isn't something spring can
// guess from a method name. the string is JPQL, not sql: it says LedgerEntry (the class)
// and e.amountCents (the field), not ledger_entries and amount_cents. hibernate translates
// it to real sql.
//
// COALESCE because SUM over zero rows returns null, not 0, and that would blow up on an
// empty table

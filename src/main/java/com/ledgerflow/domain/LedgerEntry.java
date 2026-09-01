package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private LedgerDirection direction;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public LedgerEntry() {
    }

    public LedgerEntry(Long paymentId, Long accountId, Long amountCents, LedgerDirection direction) {
        this.paymentId = paymentId;
        this.accountId = accountId;
        this.amountCents = amountCents;
        this.direction = direction;
    }

    @PrePersist
    void onInsert() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

// same thing as payment.java, this class is called by LedgerService.java in service/
// one line of double entry bookkeeping. one payment always writes exactly two of these:
// a DEBIT on the customer and a matching CREDIT on the merchant, same amount.
// in service/LedgerService.java, we create credit and debit instances of LedgerEntry (using payments object metadata), for each paymemt;
// thus one row = one movement of money touching one account; a payment writes two of these
//
// the point is the invariant: sum of all debits == sum of all credits, always. if those
// ever disagree, money was invented or destroyed. a status column can't tell you that.
//
// append only. never update or delete a row here. a refund writes a new reversing pair
// instead of erasing the original. that's what makes it an audit trail.
//
// EnumType.STRING not ORDINAL: ORDINAL stores 0 and 1, so reordering the enum later would
// silently flip the meaning of every existing row.
//
// no setters on purpose, past ledger entries shouldn't be editable.

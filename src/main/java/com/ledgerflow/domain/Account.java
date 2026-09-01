package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "balance_cents")
    private Long balanceCents;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Account() {
    }

    @PrePersist
    void onInsert() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.balanceCents == null) {
            this.balanceCents = 0L;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getBalanceCents() {
        return balanceCents;
    }

    public void setBalanceCents(Long balanceCents) {
        this.balanceCents = balanceCents;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

// an account money moves into or out of: a customer, a merchant, or the platform.
//
// turns custoemer into a database row
// 
// two ids on purpose. externalId is the public handle ("cust_001") that the api accepts.
// id is the internal row number that foreign keys and ledger entries point at. the api
// never sees id, so we can renumber the table without breaking clients.
//
// @PrePersist and @PreUpdate are jpa lifecycle hooks. hibernate runs them right before it
// writes the row, so timestamps can't be forgotten at a call site.

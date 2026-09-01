package com.ledgerflow.repository;

import com.ledgerflow.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // turns the public handle "cust_001" into the account row, so we can get its internal id
    Optional<Account> findByExternalId(String externalId);
}

// purpose: looks up an account by its public handle
// same as PaymentRepostiory, Spring parses the method name and auto geenrates SQL query
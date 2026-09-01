package com.ledgerflow.domain;

public enum LedgerDirection {

    // money leaving an account. for a payment this is the customer side.
    DEBIT,

    // money arriving in an account. for a payment this is the merchant side.
    CREDIT
}

// enum makes typos a compile error instead of a runtime one
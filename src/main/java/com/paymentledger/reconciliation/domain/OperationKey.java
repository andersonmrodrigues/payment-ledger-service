package com.paymentledger.reconciliation.domain;

/** How a statement line and an operation of this service find each other. */
public record OperationKey(String pspReference, OperationType type) {}

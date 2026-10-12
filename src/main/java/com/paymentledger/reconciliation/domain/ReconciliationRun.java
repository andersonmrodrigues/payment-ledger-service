package com.paymentledger.reconciliation.domain;

import java.time.Instant;
import java.util.UUID;

/** A reconciliation kept for audit: which period was checked, when, and what was found. */
public record ReconciliationRun(
    UUID id, ReconciliationPeriod period, ReconciliationReport report, Instant createdAt) {}

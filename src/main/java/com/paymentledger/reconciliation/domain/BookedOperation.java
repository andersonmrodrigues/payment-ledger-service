package com.paymentledger.reconciliation.domain;

import com.paymentledger.shared.domain.Money;
import java.util.Optional;

/**
 * An operation as this service recorded it, with the amount its journal entry moved through the PSP
 * clearing account (empty when no entry exists).
 */
public record BookedOperation(OperationKey key, Money amount, Optional<Money> clearingAmount) {}

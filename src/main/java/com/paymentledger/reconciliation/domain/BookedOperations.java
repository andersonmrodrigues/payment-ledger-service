package com.paymentledger.reconciliation.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Reads this service's operations together with what the ledger booked for them. */
public interface BookedOperations {

  List<BookedOperation> recordedBetween(Instant from, Instant to);

  List<BookedOperation> withKeys(Collection<OperationKey> keys);
}

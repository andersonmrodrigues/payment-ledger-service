package com.paymentledger.reconciliation.domain;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRunRepository {

  void add(ReconciliationRun run);

  Optional<ReconciliationRun> findById(UUID id);
}

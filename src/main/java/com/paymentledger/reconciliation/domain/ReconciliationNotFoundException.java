package com.paymentledger.reconciliation.domain;

import com.paymentledger.shared.domain.NotFoundException;
import java.util.UUID;

public class ReconciliationNotFoundException extends NotFoundException {

  public ReconciliationNotFoundException(UUID id) {
    super("Reconciliation " + id + " not found");
  }
}

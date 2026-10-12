package com.paymentledger.ledger.domain;

import java.util.Objects;

/**
 * The business operation a journal entry records. One operation is posted at most once, which is
 * what stops a retried request from booking the same money twice.
 */
public record EntryReference(String type, String id) {

  public EntryReference {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(id, "id");
  }
}

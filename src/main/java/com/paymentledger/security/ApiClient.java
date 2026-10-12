package com.paymentledger.security;

import java.util.Set;

/** The authenticated caller. Payments and idempotency keys belong to one client. */
public record ApiClient(String id, Set<Role> roles) {

  public ApiClient {
    roles = Set.copyOf(roles);
  }
}

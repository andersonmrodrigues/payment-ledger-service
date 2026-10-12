package com.paymentledger.ledger.domain;

import java.util.UUID;

/**
 * Splits a busy account into {@code count} accounts named {@code <code>#<shard>}, so postings for
 * different keys lock different rows instead of queueing on one. The account's balance is the sum
 * of its shards.
 *
 * <p>The shard follows from the key alone. Changing {@code count} later sends some reversals to a
 * different shard than the original posting: totals stay right, but a single shard may go negative.
 */
public record AccountShards(int count) {

  public static final String SEPARATOR = "#";

  public AccountShards {
    if (count < 1 || count > 1024) {
      throw new IllegalArgumentException("Shard count must be between 1 and 1024, got " + count);
    }
  }

  public String code(String code, UUID key) {
    return code + SEPARATOR + Math.floorMod(key.getLeastSignificantBits(), count);
  }
}

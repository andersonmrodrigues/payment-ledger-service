package com.paymentledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountShardsTest {

  private final AccountShards shards = new AccountShards(16);

  @Test
  void theSameKeyAlwaysGoesToTheSameShard() {
    UUID key = UUID.fromString("0b1a2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    assertThat(shards.code("PSP_CLEARING", key))
        .isEqualTo(shards.code("PSP_CLEARING", key))
        .isEqualTo("PSP_CLEARING#13");
  }

  @Test
  void randomKeysSpreadOverEveryShard() {
    Set<String> used = new HashSet<>();
    for (int i = 0; i < 2_000; i++) {
      used.add(shards.code("FEE_REVENUE", UUID.randomUUID()));
    }

    assertThat(used).hasSize(16).allMatch(code -> code.matches("FEE_REVENUE#([0-9]|1[0-5])"));
  }

  @Test
  void oneShardIsAPlainSplitOfOne() {
    assertThat(new AccountShards(1).code("FEE_REVENUE", UUID.randomUUID()))
        .isEqualTo("FEE_REVENUE#0");
  }

  @Test
  void rejectsAnUnusableCount() {
    assertThatThrownBy(() -> new AccountShards(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountShards(1025)).isInstanceOf(IllegalArgumentException.class);
  }
}

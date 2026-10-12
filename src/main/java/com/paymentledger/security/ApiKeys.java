package com.paymentledger.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/** Finds the client a presented API key belongs to. */
class ApiKeys {

  private record Entry(byte[] keyDigest, ApiClient client) {}

  private final List<Entry> entries;

  ApiKeys(SecurityProperties properties) {
    this.entries =
        properties.clients().stream()
            .flatMap(
                c -> {
                  var client = new ApiClient(c.id(), c.roles());
                  return c.keyDigests().stream().map(digest -> new Entry(digest, client));
                })
            .toList();
  }

  boolean isEmpty() {
    return entries.isEmpty();
  }

  /** Compares against every client in constant time, so timing reveals nothing about the keys. */
  Optional<ApiClient> authenticate(String presentedKey) {
    byte[] digest = sha256(presentedKey);
    ApiClient match = null;
    for (Entry entry : entries) {
      if (MessageDigest.isEqual(digest, entry.keyDigest())) {
        match = entry.client();
      }
    }
    return Optional.ofNullable(match);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JVM", e);
    }
  }
}

package com.paymentledger.security;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API clients, normally supplied by the environment or a secret manager. Only the SHA-256 of each
 * key is configured, so the configuration never holds a usable credential. A client may have
 * several keys, which is how a key is rotated without downtime. Mistakes fail startup.
 */
@ConfigurationProperties("security")
public record SecurityProperties(List<Client> clients) {

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

  public record Client(String id, List<String> keySha256, Set<Role> roles) {

    public Client {
      if (id == null || !ID.matcher(id).matches()) {
        throw new IllegalArgumentException("Client id must match " + ID + ", got " + id);
      }
      if (keySha256 == null
          || keySha256.isEmpty()
          || !keySha256.stream().allMatch(k -> k != null && SHA256_HEX.matcher(k).matches())) {
        throw new IllegalArgumentException(
            "Client " + id + ": key-sha256 must list hashes of 64 lowercase hex characters");
      }
      keySha256 = List.copyOf(keySha256);
      if (roles == null || roles.isEmpty()) {
        throw new IllegalArgumentException("Client " + id + " needs at least one role");
      }
      roles = Set.copyOf(roles);
    }

    List<byte[]> keyDigests() {
      return keySha256.stream().map(HexFormat.of()::parseHex).toList();
    }
  }

  public SecurityProperties {
    clients = clients == null ? List.of() : List.copyOf(clients);
    Set<String> ids = new HashSet<>();
    Set<String> keys = new HashSet<>();
    for (Client client : clients) {
      if (!ids.add(client.id())) {
        throw new IllegalArgumentException("Duplicate client id " + client.id());
      }
      for (String key : client.keySha256()) {
        if (!keys.add(key)) {
          throw new IllegalArgumentException("Client " + client.id() + " reuses a key");
        }
      }
    }
  }
}

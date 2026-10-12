package com.paymentledger.outbox.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Either {@code eventIds}, or a {@code from}/{@code to} window on creation time. */
public record ReplayRequest(List<UUID> eventIds, Instant from, Instant to) {

  boolean byIds() {
    return eventIds != null && !eventIds.isEmpty() && from == null && to == null;
  }

  boolean byWindow() {
    return (eventIds == null || eventIds.isEmpty()) && from != null && to != null;
  }
}

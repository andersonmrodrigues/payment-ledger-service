package com.paymentledger;

import com.paymentledger.outbox.domain.EventPublisher;
import com.paymentledger.outbox.domain.OutboxEvent;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records what was published and fails on demand, standing in for a flaky broker. */
public class RecordingEventPublisher implements EventPublisher {

  private final List<OutboxEvent> published = new CopyOnWriteArrayList<>();
  private final Set<UUID> failing = ConcurrentHashMap.newKeySet();
  private volatile boolean failAll;

  public void reset() {
    published.clear();
    failing.clear();
    failAll = false;
  }

  public void failFor(UUID eventId) {
    failing.add(eventId);
  }

  public void failAll(boolean fail) {
    failAll = fail;
  }

  public List<OutboxEvent> published() {
    return List.copyOf(published);
  }

  @Override
  public void publish(OutboxEvent event) {
    if (failAll || failing.contains(event.id())) {
      throw new IllegalStateException("broker unavailable");
    }
    published.add(event);
  }
}

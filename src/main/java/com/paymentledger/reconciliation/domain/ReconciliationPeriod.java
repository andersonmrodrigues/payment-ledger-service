package com.paymentledger.reconciliation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * The days a statement covers, {@code [start, end)} in UTC. Capped so one upload cannot pull the
 * whole history into memory; PSPs settle daily or monthly.
 */
public record ReconciliationPeriod(LocalDate start, LocalDate end) {

  public static final int MAX_DAYS = 31;

  public ReconciliationPeriod {
    if (start == null || end == null) {
      throw new InvalidPeriodException("Period start and end are required");
    }
    if (!start.isBefore(end)) {
      throw new InvalidPeriodException("Period start must be before its end");
    }
    if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
      throw new InvalidPeriodException("Period cannot exceed " + MAX_DAYS + " days");
    }
  }

  public Instant from() {
    return start.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  public Instant to() {
    return end.atStartOfDay(ZoneOffset.UTC).toInstant();
  }
}

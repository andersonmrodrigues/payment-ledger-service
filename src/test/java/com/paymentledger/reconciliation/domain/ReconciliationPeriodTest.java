package com.paymentledger.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReconciliationPeriodTest {

  private static final LocalDate START = LocalDate.parse("2026-10-01");

  @Test
  void coversWholeUtcDaysWithAnExclusiveEnd() {
    var period = new ReconciliationPeriod(START, START.plusDays(1));

    assertThat(period.from()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    assertThat(period.to()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));
  }

  @Test
  void acceptsUpToTheMaximumLength() {
    assertThat(new ReconciliationPeriod(START, START.plusDays(31)).end())
        .isEqualTo(START.plusDays(31));
  }

  @Test
  void rejectsEmptyReversedOrTooLongPeriods() {
    assertThatThrownBy(() -> new ReconciliationPeriod(START, START))
        .isInstanceOf(InvalidPeriodException.class);
    assertThatThrownBy(() -> new ReconciliationPeriod(START, START.minusDays(1)))
        .isInstanceOf(InvalidPeriodException.class);
    assertThatThrownBy(() -> new ReconciliationPeriod(START, START.plusDays(32)))
        .isInstanceOf(InvalidPeriodException.class)
        .hasMessage("Period cannot exceed 31 days");
  }
}

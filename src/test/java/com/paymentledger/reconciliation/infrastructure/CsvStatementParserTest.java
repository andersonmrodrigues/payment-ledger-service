package com.paymentledger.reconciliation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.reconciliation.domain.MalformedStatementException;
import com.paymentledger.reconciliation.domain.OperationType;
import com.paymentledger.reconciliation.domain.StatementLine;
import com.paymentledger.shared.domain.Money;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CsvStatementParserTest {

  private static final String HEADER = "psp_reference,type,amount_minor,currency,settled_on\n";
  private final CsvStatementParser parser = new CsvStatementParser();

  @Test
  void readsLinesWithTheirLineNumbers() {
    List<StatementLine> lines =
        parse(
            HEADER
                + "psp-1,CAPTURE,10000,BRL,2026-10-12\n\n psp-2 , refund ,2500,BRL,2026-10-13\n");

    assertThat(lines)
        .containsExactly(
            new StatementLine(
                2,
                "psp-1",
                OperationType.CAPTURE,
                Money.of(10_000, "BRL"),
                LocalDate.parse("2026-10-12")),
            new StatementLine(
                4,
                "psp-2",
                OperationType.REFUND,
                Money.of(2_500, "BRL"),
                LocalDate.parse("2026-10-13")));
  }

  @Test
  void ignoresAByteOrderMarkAndWindowsLineEndings() {
    List<StatementLine> lines =
        parse("﻿" + HEADER.replace("\n", "\r\n") + "psp-1,CAPTURE,1,BRL,2026-10-12\r\n");

    assertThat(lines).singleElement().extracting(StatementLine::pspReference).isEqualTo("psp-1");
  }

  @Test
  void readsAQuotedReferenceContainingAComma() {
    assertThat(parse(HEADER + "\"psp,1\",CAPTURE,1,BRL,2026-10-12\n"))
        .singleElement()
        .extracting(StatementLine::pspReference)
        .isEqualTo("psp,1");
  }

  @Test
  void anEmptyStatementWithAHeaderHasNoLines() {
    assertThat(parse(HEADER)).isEmpty();
  }

  @Test
  void rejectsAMissingOrDifferentHeader() {
    assertThatThrownBy(() -> parse(""))
        .isInstanceOf(MalformedStatementException.class)
        .hasMessageStartingWith("Header must be");
    assertThatThrownBy(() -> parse("reference,type,amount,currency,date\n"))
        .isInstanceOf(MalformedStatementException.class)
        .hasMessageStartingWith("Header must be");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "psp-1,CAPTURE,100,BRL                | Line 3: expected 5 columns, got 4",
        ",CAPTURE,100,BRL,2026-10-12          | Line 3: psp_reference is empty",
        "psp-1,CHARGEBACK,100,BRL,2026-10-12  | Line 3: invalid type 'CHARGEBACK'",
        "psp-1,CAPTURE,10.50,BRL,2026-10-12   | Line 3: invalid amount_minor '10.50'",
        "psp-1,CAPTURE,0,BRL,2026-10-12       | Line 3: amount_minor must be positive",
        "psp-1,CAPTURE,-5,BRL,2026-10-12      | Line 3: amount_minor must be positive",
        "psp-1,CAPTURE,100,REAL,2026-10-12    | Line 3: invalid currency 'REAL'",
        "psp-1,CAPTURE,100,BRL,12/10/2026     | Line 3: invalid settled_on '12/10/2026'",
      })
  void rejectsTheWholeFileNamingTheBadLine(String badLine, String message) {
    String csv = HEADER + "psp-0,CAPTURE,1,BRL,2026-10-12\n" + badLine + "\n";

    assertThatThrownBy(() -> parse(csv))
        .isInstanceOf(MalformedStatementException.class)
        .hasMessage(message);
  }

  private List<StatementLine> parse(String csv) {
    return parser.parse(new StringReader(csv));
  }
}

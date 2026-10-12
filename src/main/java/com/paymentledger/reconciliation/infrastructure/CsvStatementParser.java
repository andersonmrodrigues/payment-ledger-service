package com.paymentledger.reconciliation.infrastructure;

import com.paymentledger.reconciliation.domain.MalformedStatementException;
import com.paymentledger.reconciliation.domain.OperationType;
import com.paymentledger.reconciliation.domain.StatementLine;
import com.paymentledger.shared.domain.Money;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Reads a PSP settlement statement:
 *
 * <pre>
 * psp_reference,type,amount_minor,currency,settled_on
 * psp-123,CAPTURE,10000,BRL,2026-10-12
 * </pre>
 *
 * The whole file is rejected on the first bad line, naming the line: reconciling half a statement
 * would report the other half as missing.
 */
@Component
public class CsvStatementParser {

  static final List<String> HEADER =
      List.of("psp_reference", "type", "amount_minor", "currency", "settled_on");

  private static final CSVFormat FORMAT =
      CSVFormat.RFC4180
          .builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setIgnoreEmptyLines(true)
          .setTrim(true)
          .get();

  public List<StatementLine> parse(Reader statement) {
    try (CSVParser parser = FORMAT.parse(skipByteOrderMark(statement))) {
      if (!parser.getHeaderNames().equals(HEADER)) {
        throw new MalformedStatementException(
            "Header must be " + String.join(",", HEADER) + ", got " + parser.getHeaderNames());
      }
      List<StatementLine> lines = new ArrayList<>();
      for (CSVRecord record : parser) {
        // the physical line, so blank lines in the file do not shift what the error names
        lines.add(toLine(record, Math.toIntExact(parser.getCurrentLineNumber())));
      }
      return lines;
    } catch (IOException | UncheckedIOException e) {
      throw new MalformedStatementException("Unreadable statement: " + e.getMessage());
    }
  }

  private static StatementLine toLine(CSVRecord record, int line) {
    if (record.size() != HEADER.size()) {
      throw new MalformedStatementException(
          "Line " + line + ": expected " + HEADER.size() + " columns, got " + record.size());
    }
    String pspReference = field(record, line, "psp_reference", Function.identity());
    OperationType type =
        field(record, line, "type", v -> OperationType.valueOf(v.toUpperCase(Locale.ROOT)));
    long amount = field(record, line, "amount_minor", Long::parseLong);
    Currency currency = field(record, line, "currency", Currency::getInstance);
    LocalDate settledOn = field(record, line, "settled_on", LocalDate::parse);
    if (amount <= 0) {
      throw new MalformedStatementException("Line " + line + ": amount_minor must be positive");
    }
    return new StatementLine(line, pspReference, type, new Money(amount, currency), settledOn);
  }

  private static <T> T field(
      CSVRecord record, int line, String column, Function<String, T> converter) {
    String value = record.get(column);
    if (value.isEmpty()) {
      throw new MalformedStatementException("Line " + line + ": " + column + " is empty");
    }
    try {
      return converter.apply(value);
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw new MalformedStatementException(
          "Line " + line + ": invalid " + column + " '" + value + "'");
    }
  }

  /**
   * Spreadsheet exports often start with a UTF-8 byte order mark, which would corrupt the header.
   */
  private static Reader skipByteOrderMark(Reader reader) throws IOException {
    var pushback = new PushbackReader(reader, 1);
    int first = pushback.read();
    if (first != -1 && first != '﻿') {
      pushback.unread(first);
    }
    return pushback;
  }
}

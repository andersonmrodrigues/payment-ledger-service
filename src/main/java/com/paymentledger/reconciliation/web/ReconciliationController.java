package com.paymentledger.reconciliation.web;

import com.paymentledger.reconciliation.application.ReconciliationService;
import com.paymentledger.reconciliation.domain.ReconciliationPeriod;
import com.paymentledger.reconciliation.domain.StatementLine;
import com.paymentledger.reconciliation.infrastructure.CsvStatementParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/reconciliations")
class ReconciliationController {

  private final ReconciliationService reconciliations;
  private final CsvStatementParser parser;

  ReconciliationController(ReconciliationService reconciliations, CsvStatementParser parser) {
    this.reconciliations = reconciliations;
    this.parser = parser;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<ReconciliationResponse> reconcile(
      @RequestPart("statement") MultipartFile statement,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd)
      throws IOException {
    var period = new ReconciliationPeriod(periodStart, periodEnd);
    List<StatementLine> lines;
    try (Reader reader =
        new InputStreamReader(statement.getInputStream(), StandardCharsets.UTF_8)) {
      lines = parser.parse(reader);
    }
    var response = ReconciliationResponse.from(reconciliations.reconcile(period, lines));
    return ResponseEntity.created(URI.create("/reconciliations/" + response.id())).body(response);
  }

  @GetMapping("/{id}")
  ReconciliationResponse get(@PathVariable UUID id) {
    return ReconciliationResponse.from(reconciliations.get(id));
  }
}

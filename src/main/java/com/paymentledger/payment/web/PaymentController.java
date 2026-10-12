package com.paymentledger.payment.web;

import com.paymentledger.idempotency.IdempotentExecutor;
import com.paymentledger.idempotency.IdempotentResult;
import com.paymentledger.payment.application.PaymentService;
import com.paymentledger.shared.domain.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/payments")
class PaymentController {

  static final String IDEMPOTENCY_KEY = "Idempotency-Key";
  static final String REPLAYED = "Idempotent-Replayed";

  private final PaymentService payments;
  private final IdempotentExecutor idempotent;

  PaymentController(PaymentService payments, IdempotentExecutor idempotent) {
    this.payments = payments;
    this.idempotent = idempotent;
  }

  @PostMapping
  ResponseEntity<PaymentResponse> capture(
      @RequestHeader(IDEMPOTENCY_KEY) @Size(min = 1, max = 255) String key,
      @Valid @RequestBody CapturePaymentRequest request) {
    Money amount = new Money(request.amountMinor(), currency(request.currency()));
    return created(
        idempotent.execute(
            "capture-payment",
            key,
            request,
            PaymentResponse.class,
            () ->
                PaymentResponse.from(
                    payments.capture(request.merchantId(), amount, request.pspReference()))));
  }

  @PostMapping("/{paymentId}/refunds")
  ResponseEntity<RefundResponse> refund(
      @RequestHeader(IDEMPOTENCY_KEY) @Size(min = 1, max = 255) String key,
      @PathVariable UUID paymentId,
      @Valid @RequestBody RefundRequest request) {
    record Fingerprint(UUID paymentId, RefundRequest body) {}
    return created(
        idempotent.execute(
            "refund-payment",
            key,
            new Fingerprint(paymentId, request),
            RefundResponse.class,
            () ->
                RefundResponse.from(
                    payments.refund(paymentId, request.amountMinor(), request.pspReference()))));
  }

  @GetMapping("/{paymentId}")
  PaymentResponse get(@PathVariable UUID paymentId) {
    return PaymentResponse.from(payments.get(paymentId));
  }

  private static <T> ResponseEntity<T> created(IdempotentResult<T> result) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(REPLAYED, Boolean.toString(result.replayed()))
        .body(result.response());
  }

  private static Currency currency(String code) {
    try {
      return Currency.getInstance(code);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency " + code);
    }
  }
}

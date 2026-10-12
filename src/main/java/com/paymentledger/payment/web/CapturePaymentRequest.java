package com.paymentledger.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CapturePaymentRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String merchantId,
    @Positive long amountMinor,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotBlank @Size(max = 100) String pspReference) {}

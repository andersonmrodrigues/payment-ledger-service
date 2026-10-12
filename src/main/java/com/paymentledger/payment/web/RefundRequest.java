package com.paymentledger.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RefundRequest(
    @Positive long amountMinor, @NotBlank @Size(max = 100) String pspReference) {}

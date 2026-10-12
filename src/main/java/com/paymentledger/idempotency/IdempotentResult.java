package com.paymentledger.idempotency;

/**
 * @param replayed true when the response was stored by an earlier request with the same key
 */
public record IdempotentResult<T>(T response, boolean replayed) {}

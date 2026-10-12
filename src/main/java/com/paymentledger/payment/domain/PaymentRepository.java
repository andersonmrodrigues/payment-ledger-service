package com.paymentledger.payment.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

  /**
   * @throws DuplicatePspReferenceException when the PSP reference is already recorded
   */
  void add(Payment payment);

  /**
   * @throws DuplicatePspReferenceException when the PSP reference is already recorded
   */
  void addRefund(Refund refund);

  Optional<Payment> findById(UUID id);

  /** Loads the payment and locks it until the transaction ends, serializing its refunds. */
  Optional<Payment> findByIdForUpdate(UUID id);
}

package in.rsh.cab.payment.internal.persistence;

import in.rsh.cab.payment.DriverEarning;
import in.rsh.cab.payment.Payment;
import in.rsh.cab.payment.PaymentAccount;
import in.rsh.cab.payment.ProviderEvent;
import in.rsh.cab.payment.Refund;
import in.rsh.cab.payment.SettlementBatch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

  PaymentAccount activeAccount(
      UUID tenantId, String provider, String configReference, String secretReference, Instant now);

  Optional<PaymentAccount> findAccount(UUID accountId);

  Optional<UUID> findTenantForAccount(UUID accountId);

  Optional<PaymentAccount> findAccountForPayment(UUID tenantId, UUID paymentId);

  void insertCaptureRequest(UUID tenantId, UUID accountId, Payment payment, UUID attemptId);

  Optional<Payment> findOwn(UUID tenantId, UUID riderAccountId, UUID paymentId);

  Optional<Payment> findByRide(UUID tenantId, UUID riderAccountId, UUID rideId);

  Optional<Payment> find(UUID tenantId, UUID paymentId);

  boolean markCaptureProcessing(UUID tenantId, UUID paymentId);

  void markCaptureSubmitted(UUID tenantId, UUID paymentId, String providerPaymentId,
      String providerRequestId);

  void markCaptureSubmissionRetryable(UUID tenantId, UUID paymentId, String failureCode);

  void insertRefund(UUID tenantId, Refund refund, UUID requesterId);

  Optional<Refund> findRefund(UUID tenantId, UUID refundId);

  long committedRefundMinor(UUID tenantId, UUID paymentId);

  boolean markRefundProcessing(UUID tenantId, UUID refundId);

  void markRefundSubmitted(UUID tenantId, UUID refundId, String providerRefundId, Instant now);

  boolean insertProviderEvent(
      UUID id, PaymentAccount account, ProviderEvent event, Instant receivedAt);

  boolean applyCaptureEvent(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded,
      int commissionBasisPoints);

  boolean applyRefundEvent(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded);

  boolean applyPayoutEvent(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded);

  void markProviderEvent(UUID accountId, String eventId, boolean applied, Instant now);

  void postCaptureLedger(UUID tenantId, UUID paymentId, Instant now);

  void postRefundLedger(UUID tenantId, UUID refundId, Instant now);

  void postPayoutReleaseLedger(UUID tenantId, UUID payoutId, Instant now);

  List<DriverEarning> earnings(UUID tenantId, UUID driverId);

  SettlementBatch createSettlement(
      UUID tenantId, UUID actorId, UUID paymentAccountId, String currency, Instant now);

  Optional<SettlementBatch.Payout> findPayout(UUID tenantId, UUID payoutId);

  boolean markPayoutProcessing(UUID tenantId, UUID payoutId);

  void markPayoutSubmitted(
      UUID tenantId, UUID payoutId, String providerPayoutId, String providerRequestId, Instant now);

  void markPayoutSubmissionRetryable(UUID tenantId, UUID payoutId, String failureCode);

  List<SettlementBatch> settlements(UUID tenantId);
}

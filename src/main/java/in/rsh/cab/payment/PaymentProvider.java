package in.rsh.cab.payment;

import java.time.Instant;
import java.util.UUID;

public interface PaymentProvider {

  String name();

  Submission capture(
      PaymentAccount account, UUID paymentId, long amountMinor, String currency,
      String idempotencyKey);

  Submission refund(
      PaymentAccount account, UUID paymentId, UUID refundId, String providerPaymentId,
      long amountMinor, String currency, String idempotencyKey);

  Submission payout(
      PaymentAccount account, UUID payoutId, UUID driverId, long amountMinor, String currency,
      String idempotencyKey);

  boolean verifies(PaymentAccount account, Instant timestamp, String rawBody, String signature);

  record Submission(String providerObjectId, String providerRequestId) {}
}

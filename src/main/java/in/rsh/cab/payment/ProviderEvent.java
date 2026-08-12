package in.rsh.cab.payment;

import java.util.UUID;

public record ProviderEvent(
    String eventId,
    Type type,
    UUID paymentId,
    UUID refundId,
    UUID payoutId,
    String providerObjectId,
    long providerVersion,
    Long amountMinor,
    String currency,
    String failureCode) {

  public enum Type {
    CAPTURE_SUCCEEDED,
    CAPTURE_FAILED,
    REFUND_SUCCEEDED,
    REFUND_FAILED,
    PAYOUT_SUCCEEDED,
    PAYOUT_FAILED
  }
}

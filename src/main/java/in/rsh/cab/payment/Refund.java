package in.rsh.cab.payment;

import java.time.Instant;
import java.util.UUID;

public record Refund(
    UUID id,
    UUID paymentId,
    long amountMinor,
    String currency,
    String reason,
    RefundState state,
    String providerRefundId,
    long providerVersion,
    long version,
    Instant createdAt,
    Instant updatedAt) {}

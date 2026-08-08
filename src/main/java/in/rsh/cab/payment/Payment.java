package in.rsh.cab.payment;

import java.time.Instant;
import java.util.UUID;

public record Payment(
    UUID id,
    UUID rideId,
    UUID riderAccountId,
    long amountMinor,
    long authorizedMinor,
    long capturedMinor,
    String currency,
    PaymentState state,
    String providerPaymentId,
    long providerVersion,
    long version,
    String failureCode,
    Instant createdAt,
    Instant updatedAt) {}

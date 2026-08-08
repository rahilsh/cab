package in.rsh.cab.dispatch;

import java.time.Instant;
import java.util.UUID;

public record DriverOffer(
    UUID id,
    UUID attemptId,
    UUID rideId,
    UUID shiftId,
    UUID driverId,
    UUID vehicleId,
    DriverOfferStatus status,
    Instant expiresAt,
    Instant respondedAt,
    Instant createdAt,
    Instant updatedAt,
    long version) {}

package in.rsh.cab.rating;

import java.time.Instant;
import java.util.UUID;

public record Rating(
    UUID id,
    UUID rideId,
    UUID reviewerAccountId,
    UUID revieweeAccountId,
    String reviewerRole,
    String revieweeRole,
    int score,
    String comment,
    String moderationStatus,
    Instant createdAt) {}

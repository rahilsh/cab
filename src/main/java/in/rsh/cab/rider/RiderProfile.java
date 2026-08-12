package in.rsh.cab.rider;

import java.time.Instant;
import java.util.UUID;

public record RiderProfile(
    UUID id,
    String displayName,
    String phoneNumber,
    Instant createdAt,
    Instant updatedAt) {}

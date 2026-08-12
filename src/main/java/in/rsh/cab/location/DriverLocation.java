package in.rsh.cab.location;

import in.rsh.cab.geography.GeoPoint;
import java.time.Instant;
import java.util.UUID;

public record DriverLocation(UUID shiftId, GeoPoint point, Instant recordedAt, long sequence) {}

package in.rsh.cab.location;

import in.rsh.cab.geography.GeoPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LiveLocationStore {

  boolean update(UUID tenantId, DriverLocation location);

  List<UUID> nearby(
      UUID tenantId, GeoPoint point, double radiusMeters, int limit, Instant now, Duration maxAge);
}

package in.rsh.cab.ride.internal.persistence;

import in.rsh.cab.ride.Ride;
import in.rsh.cab.ride.RideStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository {

  void insert(UUID tenantId, Ride ride);

  Optional<Ride> find(UUID tenantId, UUID rideId);

  Optional<Ride> findOwn(UUID tenantId, UUID riderAccountId, UUID rideId);

  Optional<Ride> findAssignedToDriver(UUID tenantId, UUID driverAccountId, UUID rideId);

  List<Ride> findOwn(UUID tenantId, UUID riderAccountId);

  boolean update(UUID tenantId, Ride ride, long expectedVersion);

  void appendHistory(
      UUID tenantId, UUID rideId, RideStatus from, RideStatus to, UUID actorAccountId,
      String reason, Instant occurredAt);
}

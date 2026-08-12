package in.rsh.cab.rating.internal.persistence;

import in.rsh.cab.rating.Rating;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository {

  Optional<RideParticipants> completedRideParticipants(UUID tenantId, UUID rideId);

  void insert(UUID tenantId, Rating rating);

  record RideParticipants(UUID riderAccountId, UUID driverAccountId) {}
}

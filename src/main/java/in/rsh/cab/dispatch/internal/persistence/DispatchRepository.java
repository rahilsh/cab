package in.rsh.cab.dispatch.internal.persistence;

import in.rsh.cab.dispatch.DriverOffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchRepository {

  void insertAttempt(
      UUID tenantId, UUID attemptId, UUID rideId, int radiusMeters, int candidateLimit,
      int candidateCount, String status, Instant now);

  void insertOffer(UUID tenantId, DriverOffer offer);

  List<DriverOffer> findOwnOffers(UUID tenantId, UUID driverAccountId, Instant now);

  Optional<DriverOffer> lockOwnOffer(UUID tenantId, UUID driverAccountId, UUID offerId);

  boolean respond(UUID tenantId, UUID offerId, String expected, String status, Instant now);

  void expireSiblings(UUID tenantId, UUID rideId, UUID acceptedOfferId, Instant now);

  void completeAttempt(UUID tenantId, UUID attemptId, String status, Instant now);

  void cancelRide(UUID tenantId, UUID rideId, Instant now);
}

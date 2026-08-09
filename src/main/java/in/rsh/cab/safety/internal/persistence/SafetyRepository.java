package in.rsh.cab.safety.internal.persistence;

import in.rsh.cab.safety.SafetyIncident;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SafetyRepository {

  boolean isRideParticipant(UUID tenantId, UUID rideId, UUID accountId);

  void insert(UUID tenantId, SafetyIncident incident);

  Optional<SafetyIncident> find(UUID tenantId, UUID incidentId);

  List<SafetyIncident> findAll(UUID tenantId);

  boolean appendEvidence(
      UUID tenantId, UUID incidentId, long expectedVersion, SafetyIncident.Evidence evidence,
      Instant now);

  boolean update(
      UUID tenantId, UUID incidentId, String expectedState, String state, String severity,
      long expectedVersion, Instant now);

  void appendAction(UUID tenantId, UUID incidentId, UUID actorId, String action,
      String fromState, String toState, String note, Instant now);
}

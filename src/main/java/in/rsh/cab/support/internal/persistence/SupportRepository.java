package in.rsh.cab.support.internal.persistence;

import in.rsh.cab.support.SupportCase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportRepository {

  boolean isRideParticipant(UUID tenantId, UUID rideId, UUID accountId);

  void insert(UUID tenantId, SupportCase supportCase, SupportCase.Message initialMessage);

  List<SupportCase> findOwn(UUID tenantId, UUID accountId);

  List<SupportCase> findAll(UUID tenantId);

  Optional<SupportCase> find(UUID tenantId, UUID caseId);

  void insertMessage(UUID tenantId, UUID caseId, SupportCase.Message message);

  boolean updateState(
      UUID tenantId, UUID caseId, String expectedState, String state, long expectedVersion,
      Instant now);

  void appendState(UUID tenantId, UUID caseId, String from, String to, UUID actorId,
      String reason, Instant now);

  boolean hasStaffRole(UUID tenantId, UUID accountId);

  boolean assign(
      UUID tenantId, UUID caseId, UUID assigneeId, UUID actorId, long expectedVersion, Instant now);
}

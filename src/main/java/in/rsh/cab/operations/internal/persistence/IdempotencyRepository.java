package in.rsh.cab.operations.internal.persistence;

import in.rsh.cab.operations.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public interface IdempotencyRepository {

  boolean insertReservation(
      UUID id, UUID tenantId, UUID actorAccountId, String operation, String key,
      String requestHash, Instant now, Instant expiresAt);

  Optional<IdempotencyRecord> lock(
      UUID tenantId, UUID actorAccountId, String operation, String key);

  void replaceReservation(
      UUID tenantId, UUID actorAccountId, UUID oldId, UUID newId,
      String requestHash, Instant now, Instant expiresAt);

  void complete(
      UUID tenantId, UUID actorAccountId, UUID recordId, String resourceType, UUID resourceId,
      int httpStatus, JsonNode safeResponse, Instant now);

  void fail(UUID tenantId, UUID actorAccountId, UUID recordId, Instant now);
}

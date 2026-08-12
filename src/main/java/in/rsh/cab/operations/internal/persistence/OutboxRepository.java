package in.rsh.cab.operations.internal.persistence;

import in.rsh.cab.operations.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public interface OutboxRepository {

  void insert(
      UUID id, UUID tenantId, String aggregateType, UUID aggregateId, long aggregateVersion,
      String eventType, int eventVersion, JsonNode payload, Instant occurredAt, Instant availableAt,
      String correlationId, UUID causationId);

  List<OutboxEvent> lease(
      UUID tenantId, int limit, Instant now, Instant leaseExpiresAt, UUID leaseToken);

  void markPublished(UUID tenantId, UUID eventId, UUID leaseToken, Instant publishedAt);

  void markRetry(UUID tenantId, UUID eventId, UUID leaseToken, Instant availableAt, String error);

  void markFailed(UUID tenantId, UUID eventId, UUID leaseToken, String error);
}

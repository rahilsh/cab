package in.rsh.cab.audit.internal.persistence;

import in.rsh.cab.audit.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public interface AuditRepository {

  void insert(
      UUID id, UUID tenantId, UUID actorAccountId, String action, String targetType, UUID targetId,
      String outcome, String correlationId, JsonNode summary, Instant occurredAt);

  List<AuditEvent> findLatest(UUID tenantId, int limit);
}

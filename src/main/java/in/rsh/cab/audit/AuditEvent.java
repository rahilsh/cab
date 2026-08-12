package in.rsh.cab.audit;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record AuditEvent(
    UUID id,
    UUID actorAccountId,
    String action,
    String targetType,
    UUID targetId,
    String outcome,
    String correlationId,
    JsonNode summary,
    Instant occurredAt) {}

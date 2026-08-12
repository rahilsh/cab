package in.rsh.cab.operations;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record OutboxEvent(
    UUID id,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    String eventType,
    int eventVersion,
    JsonNode payload,
    Instant occurredAt,
    String correlationId,
    UUID causationId,
    int attempts) {}

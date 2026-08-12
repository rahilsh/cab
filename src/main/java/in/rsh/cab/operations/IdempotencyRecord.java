package in.rsh.cab.operations;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record IdempotencyRecord(
    UUID id,
    UUID tenantId,
    UUID actorAccountId,
    String operation,
    String key,
    String requestHash,
    IdempotencyState state,
    UUID resourceId,
    int httpStatus,
    JsonNode safeResponse,
    Instant expiresAt) {}

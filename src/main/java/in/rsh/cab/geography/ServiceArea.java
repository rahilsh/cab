package in.rsh.cab.geography;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ServiceArea(
    UUID id,
    String slug,
    String name,
    String status,
    String timezone,
    JsonNode boundary,
    Instant createdAt,
    Instant updatedAt) {}

package in.rsh.cab.pricing;

import java.time.Instant;
import java.util.UUID;

public record ServiceProduct(
    UUID id,
    String slug,
    String name,
    ProductStatus status,
    int capacity,
    String serviceClass,
    Instant createdAt,
    Instant updatedAt) {}

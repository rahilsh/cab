package in.rsh.cab.pricing;

import java.time.Instant;
import java.util.UUID;

public record PricingRule(
    UUID id,
    UUID productId,
    int version,
    Instant effectiveFrom,
    Instant effectiveTo,
    long baseFareMinor,
    long perKmMinor,
    long perMinuteMinor,
    long minimumFareMinor,
    String currency,
    Integer surgeBasisPoints,
    Integer taxBasisPoints,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}

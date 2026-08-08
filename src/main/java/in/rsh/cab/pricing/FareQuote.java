package in.rsh.cab.pricing;

import in.rsh.cab.geography.GeoPoint;
import java.time.Instant;
import java.util.UUID;

public record FareQuote(
    UUID id,
    UUID productId,
    UUID pricingRuleId,
    int pricingRuleVersion,
    GeoPoint pickup,
    GeoPoint dropoff,
    long routeDistanceMeters,
    long routeDurationSeconds,
    long baseRateMinor,
    long perKmRateMinor,
    long perMinuteRateMinor,
    long minimumFareMinor,
    int surgeBasisPoints,
    int taxBasisPoints,
    long baseFareMinor,
    long distanceFareMinor,
    long timeFareMinor,
    long minimumAdjustmentMinor,
    long subtotalMinor,
    long surgeMinor,
    long taxMinor,
    long totalMinor,
    String currency,
    QuoteStatus status,
    Instant expiresAt,
    String requestFingerprint,
    Instant createdAt,
    Instant updatedAt,
    long version) {}

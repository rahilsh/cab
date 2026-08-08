package in.rsh.cab.pricing;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.geography.internal.persistence.ServiceAreaRepository;
import in.rsh.cab.audit.AuditService;
import in.rsh.cab.operations.IdempotencyReservation;
import in.rsh.cab.operations.IdempotencyService;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.pricing.internal.persistence.PricingRepository;
import in.rsh.cab.routing.RouteEstimate;
import in.rsh.cab.routing.RouteEstimator;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PricingService {

  private static final long BASIS_POINTS = 10_000;
  private final PricingRepository pricing;
  private final ServiceAreaRepository serviceAreas;
  private final RouteEstimator routeEstimator;
  private final Clock clock;
  private final Duration quoteTtl;
  private final IdempotencyService idempotency;
  private final OutboxService outbox;
  private final AuditService audit;
  private final ObjectMapper objectMapper;

  public PricingService(
      PricingRepository pricing,
      ServiceAreaRepository serviceAreas,
      RouteEstimator routeEstimator,
      Clock clock,
      IdempotencyService idempotency,
      OutboxService outbox,
      AuditService audit,
      ObjectMapper objectMapper,
      @Value("${pricing.quote-ttl:PT10M}") Duration quoteTtl) {
    if (quoteTtl.isZero() || quoteTtl.isNegative()) {
      throw new IllegalArgumentException("Quote TTL must be positive");
    }
    this.pricing = pricing;
    this.serviceAreas = serviceAreas;
    this.routeEstimator = routeEstimator;
    this.clock = clock;
    this.idempotency = idempotency;
    this.outbox = outbox;
    this.audit = audit;
    this.objectMapper = objectMapper;
    this.quoteTtl = quoteTtl;
  }

  @Transactional
  public ServiceProduct createProduct(
      String slug, String name, ProductStatus status, int capacity, String serviceClass) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    Instant now = clock.instant();
    ServiceProduct product =
        new ServiceProduct(
            UUID.randomUUID(), slug, name, status, capacity, serviceClass, now, now);
    try {
      pricing.insertProduct(context.tenantId(), product);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Product slug or name is already in use");
    }
    return product;
  }

  @Transactional(readOnly = true)
  public List<ServiceProduct> listProducts() {
    return pricing.findProducts(require(TenantRole.TENANT_ADMIN).tenantId());
  }

  @Transactional
  public PricingRule createRule(
      UUID productId,
      Instant effectiveFrom,
      Instant effectiveTo,
      long baseFareMinor,
      long perKmMinor,
      long perMinuteMinor,
      long minimumFareMinor,
      String currency,
      Integer surgeBasisPoints,
      Integer taxBasisPoints,
      boolean active) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new InvalidRequestException("Effective end must be after effective start");
    }
    if (surgeBasisPoints != null && surgeBasisPoints > 100_000) {
      throw new InvalidRequestException("Surge basis points must not exceed 100000");
    }
    if (taxBasisPoints != null && taxBasisPoints > BASIS_POINTS) {
      throw new InvalidRequestException("Tax basis points must not exceed 10000");
    }
    validateMoney(baseFareMinor, perKmMinor, perMinuteMinor, minimumFareMinor, currency);
    if (pricing.findProduct(context.tenantId(), productId).isEmpty()) {
      throw new NotFoundException("Product not found");
    }
    Instant now = clock.instant();
    PricingRule rule =
        new PricingRule(
            UUID.randomUUID(),
            productId,
            pricing.nextRuleVersion(context.tenantId(), productId),
            effectiveFrom,
            effectiveTo,
            baseFareMinor,
            perKmMinor,
            perMinuteMinor,
            minimumFareMinor,
            new Money(0, currency).currency(),
            surgeBasisPoints,
            taxBasisPoints,
            active,
            now,
            now);
    try {
      pricing.insertRule(context.tenantId(), rule);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Pricing rule version or active effective range conflicts");
    }
    return rule;
  }

  @Transactional(readOnly = true)
  public List<PricingRule> listRules() {
    return pricing.findRules(require(TenantRole.TENANT_ADMIN).tenantId());
  }

  @Transactional
  public QuoteCreation createQuote(
      String idempotencyKey, UUID productId, GeoPoint pickup, GeoPoint dropoff) {
    TenantContext context = require(TenantRole.RIDER);
    String requestHash = fingerprint(productId, pickup, dropoff);
    IdempotencyReservation reservation = idempotency.reserve(
        context.tenantId(), context.accountId(), "fare-quote.create", idempotencyKey, requestHash);
    if (reservation.status() == IdempotencyReservation.Status.REPLAY) {
      return new QuoteCreation(
          objectMapper.treeToValue(reservation.safeResponse(), FareQuote.class),
          reservation.httpStatus(), true);
    }
    ServiceProduct product =
        pricing.findProduct(context.tenantId(), productId)
            .filter(candidate -> candidate.status() == ProductStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Active product not found"));
    if (!serviceAreas.coversRoute(context.tenantId(), pickup, dropoff)) {
      throw new ConflictException("Pickup and dropoff must be covered by one active service area");
    }

    Instant now = clock.instant();
    PricingRule rule =
        pricing.findEffectiveRule(context.tenantId(), product.id(), now)
            .orElseThrow(() -> new ConflictException("No effective pricing rule is available"));
    RouteEstimate route = routeEstimator.estimate(pickup, dropoff);
    long distanceMeters = wholeUnits(route.distanceMeters(), "Route distance");
    long durationSeconds = wholeUnits(route.durationSeconds(), "Route duration");

    try {
      FareQuote quote = calculateQuote(product.id(), rule, pickup, dropoff, distanceMeters, durationSeconds, now);
      pricing.insertQuote(context.tenantId(), context.accountId(), quote);
      JsonNode safeQuote = objectMapper.valueToTree(quote);
      outbox.append(
          context.tenantId(), "fare_quote", quote.id(), quote.version(), "fare_quote.created", 1,
          objectMapper.valueToTree(new FareQuoteCreated(quote.id(), quote.productId(),
              quote.totalMinor(), quote.currency(), quote.expiresAt())), null);
      audit.record(
          context.tenantId(), context.accountId(), "fare_quote.create", "fare_quote", quote.id(),
          "SUCCESS", objectMapper.valueToTree(new FareQuoteAuditSummary(
              quote.productId(), quote.totalMinor(), quote.currency())));
      idempotency.complete(
          context.tenantId(), context.accountId(), reservation.recordId(), "fare_quote", quote.id(),
          201, safeQuote);
      return new QuoteCreation(quote, 201, false);
    } catch (ArithmeticException exception) {
      throw new InvalidRequestException("Fare exceeds the supported monetary range");
    }
  }

  public record QuoteCreation(FareQuote quote, int httpStatus, boolean replayed) {}

  private record FareQuoteCreated(
      UUID quoteId, UUID productId, long totalMinor, String currency, Instant expiresAt) {}

  private record FareQuoteAuditSummary(UUID productId, long totalMinor, String currency) {}

  @Transactional(readOnly = true)
  public FareQuote getOwnQuote(UUID quoteId) {
    TenantContext context = require(TenantRole.RIDER);
    return pricing.findQuote(context.tenantId(), context.accountId(), quoteId)
        .orElseThrow(() -> new NotFoundException("Fare quote not found"));
  }

  @Transactional(readOnly = true)
  public List<FareQuote> listOwnQuotes() {
    TenantContext context = require(TenantRole.RIDER);
    return pricing.findQuotes(context.tenantId(), context.accountId());
  }

  private FareQuote calculateQuote(
      UUID productId,
      PricingRule rule,
      GeoPoint pickup,
      GeoPoint dropoff,
      long distanceMeters,
      long durationSeconds,
      Instant now) {
    String currency = rule.currency();
    Money base = new Money(rule.baseFareMinor(), currency);
    Money distance = new Money(rule.perKmMinor(), currency)
        .multiplyRatio(distanceMeters, 1_000, RoundingMode.HALF_UP);
    Money time = new Money(rule.perMinuteMinor(), currency)
        .multiplyRatio(durationSeconds, 60, RoundingMode.HALF_UP);
    Money calculated = base.add(distance).add(time);
    Money minimum = new Money(rule.minimumFareMinor(), currency);
    long adjustmentMinor = Math.max(0, Math.subtractExact(minimum.minorUnits(), calculated.minorUnits()));
    Money subtotal = calculated.add(new Money(adjustmentMinor, currency));
    int surgeBasisPoints = rule.surgeBasisPoints() == null ? 0 : rule.surgeBasisPoints();
    int taxBasisPoints = rule.taxBasisPoints() == null ? 0 : rule.taxBasisPoints();
    Money surge = subtotal.multiplyRatio(surgeBasisPoints, BASIS_POINTS, RoundingMode.HALF_UP);
    Money taxable = subtotal.add(surge);
    Money tax = taxable.multiplyRatio(taxBasisPoints, BASIS_POINTS, RoundingMode.HALF_UP);
    Money total = taxable.add(tax);
    return new FareQuote(
        UUID.randomUUID(), productId, rule.id(), rule.version(), pickup, dropoff,
        distanceMeters, durationSeconds, rule.baseFareMinor(), rule.perKmMinor(),
        rule.perMinuteMinor(), rule.minimumFareMinor(), surgeBasisPoints, taxBasisPoints,
        base.minorUnits(), distance.minorUnits(), time.minorUnits(), adjustmentMinor,
        subtotal.minorUnits(), surge.minorUnits(), tax.minorUnits(), total.minorUnits(),
        currency, QuoteStatus.ACTIVE, now.plus(quoteTtl), fingerprint(productId, pickup, dropoff),
        now, now, 0);
  }

  private long wholeUnits(double value, String field) {
    if (!Double.isFinite(value) || value < 0 || value > Long.MAX_VALUE) {
      throw new InvalidRequestException(field + " is outside the supported range");
    }
    return (long) Math.ceil(value);
  }

  private void validateMoney(long base, long perKm, long perMinute, long minimum, String currency) {
    if (base < 0 || perKm < 0 || perMinute < 0 || minimum < 0) {
      throw new InvalidRequestException("Pricing amounts must be non-negative");
    }
    new Money(0, currency);
  }

  private TenantContext require(TenantRole role) {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(role)) {
      throw new TenantAccessDeniedException(role + " role is required");
    }
    return context;
  }

  private String fingerprint(UUID productId, GeoPoint pickup, GeoPoint dropoff) {
    String request = productId + "|" + pickup.latitude() + "|" + pickup.longitude()
        + "|" + dropoff.latitude() + "|" + dropoff.longitude();
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}

package in.rsh.cab.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

class PricingServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private static final GeoPoint PICKUP = new GeoPoint(12.95, 77.60);
  private static final GeoPoint DROPOFF = new GeoPoint(13.00, 77.65);
  private final PricingRepository repository = mock(PricingRepository.class);
  private final ServiceAreaRepository serviceAreas = mock(ServiceAreaRepository.class);
  private final RouteEstimator routes = mock(RouteEstimator.class);
  private final IdempotencyService idempotency = mock(IdempotencyService.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final AuditService audit = mock(AuditService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private PricingService service;

  @BeforeEach
  void setUp() {
    service = new PricingService(repository, serviceAreas, routes,
        Clock.fixed(NOW, ZoneOffset.UTC), idempotency, outbox, audit, objectMapper,
        Duration.ofMinutes(10));
    when(idempotency.reserve(any(), any(), any(), any(), any()))
        .thenReturn(new IdempotencyReservation(
            IdempotencyReservation.Status.RESERVED, UUID.randomUUID(), null, 0, null));
    admin();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void validatesPositiveQuoteTtl() {
    assertThrows(IllegalArgumentException.class,
        () -> new PricingService(repository, serviceAreas, routes,
            Clock.systemUTC(), idempotency, outbox, audit, objectMapper, Duration.ZERO));
  }

  @Test
  void adminCreatesAndListsProducts() {
    ServiceProduct created = service.createProduct(
        "standard", "Standard", ProductStatus.ACTIVE, 4, "STANDARD");

    assertEquals(NOW, created.createdAt());
    verify(repository).insertProduct(TENANT_ID, created);
    when(repository.findProducts(TENANT_ID)).thenReturn(List.of(created));
    assertEquals(List.of(created), service.listProducts());
  }

  @Test
  void productCreationMapsUniquenessConflictAndRequiresAdmin() {
    doThrow(new DataIntegrityViolationException("constraint"))
        .when(repository).insertProduct(any(), any());
    assertThrows(ConflictException.class,
        () -> service.createProduct("standard", "Standard", ProductStatus.ACTIVE, 4, "STANDARD"));

    rider();
    assertThrows(TenantAccessDeniedException.class, service::listProducts);
  }

  @Test
  void adminCreatesVersionedRuleAndListsRules() {
    ServiceProduct product = product(ProductStatus.ACTIVE);
    when(repository.findProduct(TENANT_ID, PRODUCT_ID)).thenReturn(Optional.of(product));
    when(repository.nextRuleVersion(TENANT_ID, PRODUCT_ID)).thenReturn(3);

    PricingRule created = service.createRule(
        PRODUCT_ID, NOW.minusSeconds(60), NOW.plusSeconds(60), 200, 100, 20, 700,
        "usd", 1000, 500, true);

    assertEquals(3, created.version());
    assertEquals("USD", created.currency());
    verify(repository).insertRule(TENANT_ID, created);
    when(repository.findRules(TENANT_ID)).thenReturn(List.of(created));
    assertEquals(List.of(created), service.listRules());
  }

  @Test
  void ruleCreationValidatesProductRangeMoneyAndConflicts() {
    assertThrows(InvalidRequestException.class,
        () -> service.createRule(PRODUCT_ID, NOW, NOW, 1, 1, 1, 1, "USD", null, null, true));
    assertThrows(InvalidRequestException.class,
        () -> service.createRule(PRODUCT_ID, NOW, null, -1, 1, 1, 1, "USD", null, null, true));
    assertThrows(IllegalArgumentException.class,
        () -> service.createRule(PRODUCT_ID, NOW, null, 1, 1, 1, 1, "BAD", null, null, true));
    assertThrows(InvalidRequestException.class,
        () -> service.createRule(PRODUCT_ID, NOW, null, 1, 1, 1, 1, "USD", 100001, null, true));
    assertThrows(InvalidRequestException.class,
        () -> service.createRule(PRODUCT_ID, NOW, null, 1, 1, 1, 1, "USD", null, 10001, true));
    assertThrows(NotFoundException.class,
        () -> service.createRule(PRODUCT_ID, NOW, null, 1, 1, 1, 1, "USD", null, null, true));

    when(repository.findProduct(TENANT_ID, PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
    doThrow(new DataIntegrityViolationException("constraint")).when(repository).insertRule(any(), any());
    assertThrows(ConflictException.class,
        () -> service.createRule(PRODUCT_ID, NOW, null, 1, 1, 1, 1, "USD", null, null, true));
  }

  @Test
  void riderCreatesDeterministicImmutableQuote() {
    rider();
    PricingRule rule = rule(200, 100, 20, 700, 1000, 500);
    when(repository.findProduct(TENANT_ID, PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
    when(serviceAreas.coversRoute(TENANT_ID, PICKUP, DROPOFF)).thenReturn(true);
    when(repository.findEffectiveRule(TENANT_ID, PRODUCT_ID, NOW)).thenReturn(Optional.of(rule));
    when(routes.estimate(PICKUP, DROPOFF)).thenReturn(new RouteEstimate(2450.5, 480));

    FareQuote quote = service.createQuote("quote-key", PRODUCT_ID, PICKUP, DROPOFF).quote();

    assertEquals(2451, quote.routeDistanceMeters());
    assertEquals(245, quote.distanceFareMinor());
    assertEquals(160, quote.timeFareMinor());
    assertEquals(95, quote.minimumAdjustmentMinor());
    assertEquals(700, quote.subtotalMinor());
    assertEquals(70, quote.surgeMinor());
    assertEquals(39, quote.taxMinor());
    assertEquals(809, quote.totalMinor());
    assertEquals(NOW.plusSeconds(600), quote.expiresAt());
    assertEquals(64, quote.requestFingerprint().length());
    assertEquals(QuoteStatus.ACTIVE, quote.status());
    assertEquals(0, quote.version());
    verify(repository).insertQuote(TENANT_ID, ACCOUNT_ID, quote);
    verify(outbox).append(any(), any(), any(), any(Long.class), any(), any(Integer.class), any(), any());
    verify(audit).record(any(), any(), any(), any(), any(), any(), any());
    verify(idempotency).complete(any(), any(), any(), any(), any(), any(Integer.class), any());
  }

  @Test
  void quoteUsesZeroOptionalAdjustmentsAndNoMinimumAdjustment() {
    rider();
    PricingRule rule = rule(100, 100, 60, 0, null, null);
    when(repository.findProduct(TENANT_ID, PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
    when(serviceAreas.coversRoute(TENANT_ID, PICKUP, DROPOFF)).thenReturn(true);
    when(repository.findEffectiveRule(TENANT_ID, PRODUCT_ID, NOW)).thenReturn(Optional.of(rule));
    when(routes.estimate(PICKUP, DROPOFF)).thenReturn(new RouteEstimate(1000, 60));

    FareQuote quote = service.createQuote("quote-key", PRODUCT_ID, PICKUP, DROPOFF).quote();

    assertEquals(0, quote.minimumAdjustmentMinor());
    assertEquals(0, quote.surgeMinor());
    assertEquals(0, quote.taxMinor());
    assertEquals(260, quote.totalMinor());
  }

  @Test
  void quoteReplayReturnsStoredSafeRepresentationWithoutCreatingAgain() {
    rider();
    FareQuote stored = quote();
    when(idempotency.reserve(any(), any(), any(), any(), any()))
        .thenReturn(new IdempotencyReservation(
            IdempotencyReservation.Status.REPLAY, UUID.randomUUID(), stored.id(), 201,
            objectMapper.valueToTree(stored)));

    PricingService.QuoteCreation result =
        service.createQuote("replay-key", PRODUCT_ID, PICKUP, DROPOFF);

    assertEquals(stored, result.quote());
    assertEquals(201, result.httpStatus());
    assertEquals(true, result.replayed());
    org.mockito.Mockito.verifyNoInteractions(repository, serviceAreas, routes, outbox, audit);
  }

  @Test
  void quoteRejectsUnavailableInputsAndOverflow() {
    rider();
    assertThrows(NotFoundException.class,
        () -> service.createQuote("key-1", PRODUCT_ID, PICKUP, DROPOFF));
    when(repository.findProduct(TENANT_ID, PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.INACTIVE)));
    assertThrows(NotFoundException.class,
        () -> service.createQuote("key-2", PRODUCT_ID, PICKUP, DROPOFF));

    when(repository.findProduct(TENANT_ID, PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
    assertThrows(ConflictException.class,
        () -> service.createQuote("key-3", PRODUCT_ID, PICKUP, DROPOFF));
    when(serviceAreas.coversRoute(TENANT_ID, PICKUP, DROPOFF)).thenReturn(true);
    assertThrows(ConflictException.class,
        () -> service.createQuote("key-4", PRODUCT_ID, PICKUP, DROPOFF));

    PricingRule overflow = rule(1, Long.MAX_VALUE, 0, 0, null, null);
    when(repository.findEffectiveRule(TENANT_ID, PRODUCT_ID, NOW)).thenReturn(Optional.of(overflow));
    when(routes.estimate(PICKUP, DROPOFF)).thenReturn(new RouteEstimate(1000, 0));
    assertThrows(InvalidRequestException.class,
        () -> service.createQuote("key-5", PRODUCT_ID, PICKUP, DROPOFF));

    when(routes.estimate(PICKUP, DROPOFF)).thenReturn(new RouteEstimate(1e20, 0));
    assertThrows(InvalidRequestException.class,
        () -> service.createQuote("key-6", PRODUCT_ID, PICKUP, DROPOFF));
  }

  @Test
  void riderGetsAndListsOnlyAccountQualifiedQuotes() {
    rider();
    FareQuote quote = quote();
    when(repository.findQuote(TENANT_ID, ACCOUNT_ID, quote.id())).thenReturn(Optional.of(quote));
    when(repository.findQuotes(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of(quote));

    assertEquals(quote, service.getOwnQuote(quote.id()));
    assertEquals(List.of(quote), service.listOwnQuotes());
    assertThrows(NotFoundException.class, () -> service.getOwnQuote(UUID.randomUUID()));

    admin();
    assertThrows(TenantAccessDeniedException.class, service::listOwnQuotes);
  }

  private ServiceProduct product(ProductStatus status) {
    return new ServiceProduct(PRODUCT_ID, "standard", "Standard", status, 4, "STANDARD", NOW, NOW);
  }

  private PricingRule rule(
      long base, long perKm, long perMinute, long minimum, Integer surge, Integer tax) {
    return new PricingRule(UUID.randomUUID(), PRODUCT_ID, 2, NOW.minusSeconds(60), null,
        base, perKm, perMinute, minimum, "USD", surge, tax, true, NOW, NOW);
  }

  private FareQuote quote() {
    return new FareQuote(UUID.randomUUID(), PRODUCT_ID, UUID.randomUUID(), 1, PICKUP, DROPOFF,
        1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, "USD",
        QuoteStatus.ACTIVE, NOW.plusSeconds(60), "fingerprint", NOW, NOW, 0);
  }

  private void admin() {
    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.TENANT_ADMIN)));
  }

  private void rider() {
    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.RIDER)));
  }
}

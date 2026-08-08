package in.rsh.cab.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.dispatch.internal.persistence.DispatchRepository;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.operations.IdempotencyReservation;
import in.rsh.cab.operations.IdempotencyService;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.pricing.FareQuote;
import in.rsh.cab.pricing.QuoteStatus;
import in.rsh.cab.pricing.internal.persistence.PricingRepository;
import in.rsh.cab.ride.internal.persistence.RideRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RideServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final RideRepository rides = mock(RideRepository.class);
  private final PricingRepository pricing = mock(PricingRepository.class);
  private final DispatchRepository dispatch = mock(DispatchRepository.class);
  private final FleetRepository fleet = mock(FleetRepository.class);
  private final IdempotencyService idempotency = mock(IdempotencyService.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final AuditService audit = mock(AuditService.class);
  private RideService service;

  @BeforeEach
  void setUp() {
    service = new RideService(rides, pricing, dispatch, fleet, idempotency, outbox, audit,
        new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    context(TenantRole.RIDER);
    when(idempotency.reserve(any(), any(), any(), any(), any())).thenReturn(
        new IdempotencyReservation(IdempotencyReservation.Status.RESERVED,
            UUID.randomUUID(), null, 0, null));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void atomicallyConsumesQuoteAndCreatesRideOperations() {
    FareQuote quote = quote();
    when(pricing.consumeQuote(TENANT, ACCOUNT, quote.id(), NOW)).thenReturn(Optional.of(quote));
    Ride created = service.create("key", quote.id()).ride();
    assertEquals(RideStatus.REQUESTED, created.status());
    assertEquals(quote.totalMinor(), created.fareMinor());
    verify(rides).insert(TENANT, created);
    verify(rides).appendHistory(TENANT, created.id(), null, RideStatus.REQUESTED, ACCOUNT, null, NOW);

    when(rides.findOwn(TENANT, ACCOUNT, created.id())).thenReturn(Optional.of(created));
    when(rides.update(eq(TENANT), any(), eq(0L))).thenReturn(true);
    Ride cancelled = service.cancelOwn(created.id(), 0, "changed plans");
    assertEquals(RideStatus.CANCELLED, cancelled.status());
    verify(dispatch).cancelRide(TENANT, created.id(), NOW);
  }

  @Test
  void handlesMissingQuoteOwnershipAndConflicts() {
    assertThrows(ConflictException.class, () -> service.create("key", UUID.randomUUID()));
    assertThrows(NotFoundException.class, () -> service.getOwn(UUID.randomUUID()));
    context(TenantRole.DRIVER);
    assertThrows(TenantAccessDeniedException.class, service::listOwn);
  }

  @Test
  void driverCompletesAndReleasesAssignedShift() {
    context(TenantRole.DRIVER);
    Ride current = ride(RideStatus.IN_PROGRESS, 5);
    when(rides.findAssignedToDriver(TENANT, ACCOUNT, current.id())).thenReturn(Optional.of(current));
    when(rides.update(eq(TENANT), any(), eq(5L))).thenReturn(true);
    when(fleet.transitionShift(
            TENANT,
            current.driverShiftId(),
            ShiftStatus.ON_TRIP,
            ShiftStatus.AVAILABLE,
            NOW))
        .thenReturn(true);
    Ride completed = service.driverAction(
        current.id(), 5, RideService.DriverAction.COMPLETE, null);
    assertEquals(RideStatus.COMPLETED, completed.status());
    verify(fleet).transitionShift(TENANT, current.driverShiftId(), ShiftStatus.ON_TRIP,
        ShiftStatus.AVAILABLE, NOW);
  }

  @Test
  void rollsBackTerminalTransitionWhenShiftCannotBeReleased() {
    context(TenantRole.DRIVER);
    Ride current = ride(RideStatus.IN_PROGRESS, 5);
    when(rides.findAssignedToDriver(TENANT, ACCOUNT, current.id())).thenReturn(Optional.of(current));
    when(rides.update(eq(TENANT), any(), eq(5L))).thenReturn(true);

    assertThrows(
        ConflictException.class,
        () -> service.driverAction(current.id(), 5, RideService.DriverAction.COMPLETE, null));
  }

  private FareQuote quote() {
    return new FareQuote(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
        new GeoPoint(12.95, 77.6), new GeoPoint(13.0, 77.65), 1000, 600,
        100, 20, 10, 100, 0, 0, 100, 20, 10, 0, 130, 0, 0, 130,
        "USD", QuoteStatus.CONSUMED, NOW.plusSeconds(60), "a".repeat(64), NOW, NOW, 1);
  }

  private Ride ride(RideStatus status, long version) {
    return new Ride(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        new GeoPoint(12.95, 77.6), new GeoPoint(13.0, 77.65), 100, "USD",
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status, null, null,
        NOW.minusSeconds(60), NOW.minusSeconds(50), NOW.minusSeconds(30), NOW.minusSeconds(10),
        null, null, NOW, version);
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

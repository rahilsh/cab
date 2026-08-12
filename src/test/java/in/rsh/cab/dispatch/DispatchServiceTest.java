package in.rsh.cab.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.dispatch.internal.persistence.DispatchRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.SupplyCandidate;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.location.LiveLocationStore;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.pricing.ProductStatus;
import in.rsh.cab.pricing.ServiceProduct;
import in.rsh.cab.pricing.internal.persistence.PricingRepository;
import in.rsh.cab.ride.Ride;
import in.rsh.cab.ride.RideStatus;
import in.rsh.cab.ride.internal.persistence.RideRepository;
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
import tools.jackson.databind.ObjectMapper;

class DispatchServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final DispatchRepository dispatch = mock(DispatchRepository.class);
  private final RideRepository rides = mock(RideRepository.class);
  private final FleetRepository fleet = mock(FleetRepository.class);
  private final PricingRepository pricing = mock(PricingRepository.class);
  private final LiveLocationStore locations = mock(LiveLocationStore.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final AuditService audit = mock(AuditService.class);
  private DispatchService service;

  @BeforeEach
  void setUp() {
    service = new DispatchService(dispatch, rides, fleet, pricing, locations, outbox, audit,
        new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), 5000, 10,
        Duration.ofMinutes(2), Duration.ofSeconds(30));
    context(TenantRole.DISPATCHER);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void startsBoundedSearchAndCreatesOffers() {
    Ride requested = ride(RideStatus.REQUESTED, 0, null, null, null);
    UUID shift = UUID.randomUUID();
    UUID driver = UUID.randomUUID();
    UUID vehicle = UUID.randomUUID();
    when(rides.find(TENANT, requested.id())).thenReturn(Optional.of(requested));
    when(rides.update(eq(TENANT), any(), eq(0L))).thenReturn(true);
    when(pricing.findProduct(TENANT, requested.productId())).thenReturn(Optional.of(
        new ServiceProduct(requested.productId(), "standard", "Standard", ProductStatus.ACTIVE,
            4, "STANDARD", NOW, NOW)));
    when(locations.nearby(eq(TENANT), any(), eq(5000.0), eq(10), eq(NOW), any()))
        .thenReturn(List.of(shift));
    when(fleet.findAvailableCandidates(TENANT, List.of(shift), "STANDARD"))
        .thenReturn(List.of(new SupplyCandidate(shift, driver, vehicle)));
    List<DriverOffer> offers = service.start(requested.id(), 0);
    assertEquals(1, offers.size());
    assertEquals(driver, offers.get(0).driverId());
    verify(dispatch).insertOffer(TENANT, offers.get(0));
  }

  @Test
  void marksNoDriverAndEnforcesVersionAndRole() {
    Ride requested = ride(RideStatus.REQUESTED, 0, null, null, null);
    when(rides.find(TENANT, requested.id())).thenReturn(Optional.of(requested));
    assertThrows(ConflictException.class, () -> service.start(requested.id(), 1));
    context(TenantRole.RIDER);
    assertThrows(TenantAccessDeniedException.class, () -> service.start(requested.id(), 0));
  }

  @Test
  void acceptsOnlyPendingFreshOfferAndReservesShift() {
    context(TenantRole.DRIVER);
    UUID shift = UUID.randomUUID();
    UUID driver = UUID.randomUUID();
    UUID vehicle = UUID.randomUUID();
    Ride matching = ride(RideStatus.MATCHING, 1, null, null, null);
    DriverOffer offer = new DriverOffer(UUID.randomUUID(), UUID.randomUUID(), matching.id(), shift,
        driver, vehicle, DriverOfferStatus.PENDING, NOW.plusSeconds(30), null, NOW, NOW, 0);
    when(dispatch.lockOwnOffer(TENANT, ACCOUNT, offer.id())).thenReturn(Optional.of(offer));
    when(rides.find(TENANT, matching.id())).thenReturn(Optional.of(matching));
    when(fleet.transitionShift(TENANT, shift, ShiftStatus.AVAILABLE, ShiftStatus.RESERVED, NOW))
        .thenReturn(true);
    when(rides.update(eq(TENANT), any(), eq(1L))).thenReturn(true);
    when(dispatch.respond(TENANT, offer.id(), "PENDING", "ACCEPTED", NOW)).thenReturn(true);
    Ride assigned = service.accept(offer.id());
    assertEquals(RideStatus.DRIVER_ASSIGNED, assigned.status());

    DriverOffer expired = new DriverOffer(UUID.randomUUID(), offer.attemptId(), matching.id(), shift,
        driver, vehicle, DriverOfferStatus.PENDING, NOW, null, NOW.minusSeconds(30), NOW, 0);
    when(dispatch.lockOwnOffer(TENANT, ACCOUNT, expired.id())).thenReturn(Optional.of(expired));
    assertThrows(ConflictException.class, () -> service.accept(expired.id()));
  }

  private Ride ride(
      RideStatus status, long version, UUID driver, UUID vehicle, UUID shift) {
    return new Ride(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        new GeoPoint(12.95, 77.6), new GeoPoint(13.0, 77.65), 100, "USD",
        driver, vehicle, shift, status, null, null, NOW, null, null, null, null, null, NOW, version);
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

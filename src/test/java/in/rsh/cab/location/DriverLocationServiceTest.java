package in.rsh.cab.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.fleet.DriverShift;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.location.internal.persistence.LocationCheckpointRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DriverLocationServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final UUID SHIFT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final FleetRepository fleet = mock(FleetRepository.class);
  private final LiveLocationStore store = mock(LiveLocationStore.class);
  private final LocationCheckpointRepository checkpoints = mock(LocationCheckpointRepository.class);
  private DriverLocationService service;

  @BeforeEach
  void setUp() {
    service = new DriverLocationService(fleet, store, checkpoints,
        Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(2), Duration.ofSeconds(30));
    context(TenantRole.DRIVER);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void updatesOwnedAvailableShiftAndCheckpoint() {
    when(fleet.findShift(TENANT, SHIFT, ACCOUNT)).thenReturn(Optional.of(shift(ShiftStatus.AVAILABLE)));
    when(checkpoints.insertIfNewer(any(), any(), any())).thenReturn(true);
    DriverLocation location = service.update(SHIFT, new GeoPoint(12.95, 77.6), NOW, 4);
    assertEquals(4, location.sequence());
    verify(checkpoints).insertIfNewer(TENANT, location, NOW);
    verify(store).update(TENANT, location);
  }

  @Test
  void acceptsLocationExactlyAtMaxAgeBoundary() {
    when(fleet.findShift(TENANT, SHIFT, ACCOUNT)).thenReturn(Optional.of(shift(ShiftStatus.AVAILABLE)));
    when(checkpoints.insertIfNewer(any(), any(), any())).thenReturn(true);

    assertEquals(NOW.minusSeconds(120),
        service.update(SHIFT, new GeoPoint(12.95, 77.6), NOW.minusSeconds(120), 1).recordedAt());
  }

  @Test
  void retainsAcceptedCheckpointWhenRedisIsUnavailable() {
    when(fleet.findShift(TENANT, SHIFT, ACCOUNT)).thenReturn(Optional.of(shift(ShiftStatus.AVAILABLE)));
    when(checkpoints.insertIfNewer(any(), any(), any())).thenReturn(true);
    when(store.update(any(), any())).thenThrow(new IllegalStateException("redis unavailable"));

    assertEquals(5, service.update(SHIFT, new GeoPoint(12.95, 77.6), NOW, 5).sequence());
    verify(checkpoints).insertIfNewer(eq(TENANT), any(), eq(NOW));
  }

  @Test
  void neverAdvancesRedisBeforeDatabaseCommit() {
    when(fleet.findShift(TENANT, SHIFT, ACCOUNT)).thenReturn(Optional.of(shift(ShiftStatus.AVAILABLE)));
    when(checkpoints.insertIfNewer(any(), any(), any())).thenReturn(true);
    TransactionSynchronizationManager.initSynchronization();
    try {
      DriverLocation location = service.update(SHIFT, new GeoPoint(12.95, 77.6), NOW, 6);
      verify(store, never()).update(any(), any());

      TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
          synchronization.afterCommit());

      verify(store).update(TENANT, location);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void rejectsInvalidFreshnessOwnershipStateAndStaleRedisUpdate() {
    assertThrows(InvalidRequestException.class,
        () -> service.update(SHIFT, new GeoPoint(0, 0), NOW, -1));
    assertThrows(InvalidRequestException.class,
        () -> service.update(SHIFT, new GeoPoint(0, 0), NOW.minusSeconds(121), 1));
    assertThrows(InvalidRequestException.class,
        () -> service.update(SHIFT, new GeoPoint(0, 0), NOW.plusSeconds(31), 1));
    assertThrows(NotFoundException.class,
        () -> service.update(SHIFT, new GeoPoint(0, 0), NOW, 1));
    when(fleet.findShift(TENANT, SHIFT, ACCOUNT)).thenReturn(Optional.of(shift(ShiftStatus.OFFLINE)));
    assertThrows(ConflictException.class,
        () -> service.update(SHIFT, new GeoPoint(0, 0), NOW, 1));
    when(fleet.findShift(TENANT, SHIFT, ACCOUNT)).thenReturn(Optional.of(shift(ShiftStatus.AVAILABLE)));
    when(checkpoints.insertIfNewer(any(), any(), any())).thenReturn(false);
    assertThrows(ConflictException.class,
        () -> service.update(SHIFT, new GeoPoint(0, 0), NOW, 1));
    verify(store, never()).update(any(), any());
    context(TenantRole.RIDER);
    assertThrows(TenantAccessDeniedException.class,
        () -> service.update(SHIFT, new GeoPoint(0, 0), NOW, 1));
  }

  private DriverShift shift(ShiftStatus status) {
    return new DriverShift(SHIFT, UUID.randomUUID(), UUID.randomUUID(), status, 0,
        NOW, NOW, null, null);
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

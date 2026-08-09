package in.rsh.cab.location;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.location.internal.persistence.LocationCheckpointRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriverLocationReconcilerTest {

  @Test
  void repairsLatestEligibleLocationsAndContinuesAfterOneFailure() {
    UUID tenant = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-08T10:00:00Z");
    LocationCheckpointRepository checkpoints = mock(LocationCheckpointRepository.class);
    LiveLocationStore locations = mock(LiveLocationStore.class);
    DriverLocation first = new DriverLocation(UUID.randomUUID(), new GeoPoint(1, 2), now, 1);
    DriverLocation second = new DriverLocation(UUID.randomUUID(), new GeoPoint(3, 4), now, 2);
    when(checkpoints.findLatestEligibleAfter(tenant, now.minusSeconds(120),
        LocalDate.of(2026, 8, 8), null, 20)).thenReturn(List.of(first, second));
    when(checkpoints.findLatestEligibleAfter(tenant, now.minusSeconds(120),
        LocalDate.of(2026, 8, 8), second.shiftId(), 20)).thenReturn(List.of());
    when(locations.isCurrent(tenant, second)).thenReturn(true);
    when(locations.update(tenant, first)).thenThrow(new IllegalStateException("redis unavailable"));

    new DriverLocationReconciler(checkpoints, locations, Clock.fixed(now, ZoneOffset.UTC),
        Duration.ofMinutes(2)).reconcile(tenant, 20);

    verify(locations).update(tenant, first);
    verify(locations).isCurrent(tenant, second);
    verify(locations, never()).update(tenant, second);
  }

  @Test
  void advancesTenantCursorAndWrapsAfterLastPage() {
    UUID tenant = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-08T10:00:00Z");
    LocationCheckpointRepository checkpoints = mock(LocationCheckpointRepository.class);
    LiveLocationStore locations = mock(LiveLocationStore.class);
    DriverLocation location = new DriverLocation(
        UUID.randomUUID(), new GeoPoint(1, 2), now, 1);
    when(checkpoints.findLatestEligibleAfter(tenant, now.minusSeconds(120),
        LocalDate.of(2026, 8, 8), null, 20)).thenReturn(List.of(location));
    when(checkpoints.findLatestEligibleAfter(tenant, now.minusSeconds(120),
        LocalDate.of(2026, 8, 8), location.shiftId(), 20)).thenReturn(List.of());
    DriverLocationReconciler reconciler = new DriverLocationReconciler(
        checkpoints, locations, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(2));

    reconciler.reconcile(tenant, 20);
    reconciler.reconcile(tenant, 20);

    verify(checkpoints).findLatestEligibleAfter(tenant, now.minusSeconds(120),
        LocalDate.of(2026, 8, 8), location.shiftId(), 20);
    verify(checkpoints, times(2)).findLatestEligibleAfter(tenant, now.minusSeconds(120),
        LocalDate.of(2026, 8, 8), null, 20);
    verify(locations, times(2)).update(tenant, location);
  }
}

package in.rsh.cab.location;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    when(checkpoints.findLatestEligible(tenant, now.minusSeconds(120),
        LocalDate.of(2026, 8, 8), 20)).thenReturn(List.of(first, second));
    when(locations.update(tenant, first)).thenThrow(new IllegalStateException("redis unavailable"));

    new DriverLocationReconciler(checkpoints, locations, Clock.fixed(now, ZoneOffset.UTC),
        Duration.ofMinutes(2)).reconcile(tenant, 20);

    verify(locations).update(tenant, second);
    verify(checkpoints).findLatestEligible(eq(tenant), eq(now.minusSeconds(120)),
        eq(LocalDate.of(2026, 8, 8)), eq(20));
  }
}

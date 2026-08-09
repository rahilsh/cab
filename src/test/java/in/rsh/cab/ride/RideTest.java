package in.rsh.cab.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.geography.GeoPoint;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RideTest {

  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

  @Test
  void followsCompleteLifecycle() {
    Ride requested = ride(RideStatus.REQUESTED);
    Ride matching = requested.matching(NOW.plusSeconds(1));
    Ride assigned = matching.assign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        NOW.plusSeconds(2));
    Ride arriving = assigned.arriving(NOW.plusSeconds(3));
    Ride arrived = arriving.arrived(NOW.plusSeconds(4));
    Ride started = arrived.start(NOW.plusSeconds(5));
    Ride completed = started.complete(NOW.plusSeconds(6));

    assertEquals(RideStatus.COMPLETED, completed.status());
    assertEquals(6, completed.version());
    assertNotNull(completed.assignedAt());
    assertNotNull(completed.arrivedAt());
    assertNotNull(completed.startedAt());
    assertNotNull(completed.completedAt());
    assertThrows(ConflictException.class, () -> completed.cancel(
        CancellationActor.RIDER, "late", NOW.plusSeconds(7)));
  }

  @Test
  void supportsNoDriverAndCancellationPolicies() {
    Ride noDriver = ride(RideStatus.REQUESTED).matching(NOW).noDriver(NOW);
    assertEquals(RideStatus.NO_DRIVER, noDriver.status());
    assertEquals(RideStatus.MATCHING, noDriver.retryMatching(NOW.plusSeconds(1)).status());
    Ride assigned = ride(RideStatus.MATCHING).assign(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);
    Ride cancelled = assigned.cancel(CancellationActor.DRIVER, "vehicle issue", NOW.plusSeconds(1));
    assertEquals(RideStatus.CANCELLED, cancelled.status());
    assertEquals(CancellationActor.DRIVER, cancelled.cancellationActor());
    assertThrows(ConflictException.class, () -> ride(RideStatus.REQUESTED)
        .cancel(CancellationActor.DRIVER, "declined", NOW));
    assertThrows(ConflictException.class, () -> ride(RideStatus.NO_DRIVER)
        .cancel(CancellationActor.RIDER, "late", NOW));
    assertThrows(ConflictException.class, () -> ride(RideStatus.IN_PROGRESS)
        .cancel(CancellationActor.RIDER, "late", NOW));
    assertThrows(InvalidRequestException.class, () -> ride(RideStatus.REQUESTED)
        .cancel(CancellationActor.RIDER, " ", NOW));
    assertThrows(ConflictException.class, () -> ride(RideStatus.REQUESTED).arriving(NOW));
  }

  private Ride ride(RideStatus status) {
    return new Ride(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        new GeoPoint(12.95, 77.6), new GeoPoint(13.0, 77.65), 809, "USD",
        null, null, null, status, null, null, NOW, null, null, null, null, null, NOW, 0);
  }
}

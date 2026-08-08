package in.rsh.cab.ride;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.geography.GeoPoint;
import java.time.Instant;
import java.util.UUID;

public record Ride(
    UUID id,
    UUID riderAccountId,
    UUID quoteId,
    UUID productId,
    GeoPoint pickup,
    GeoPoint dropoff,
    long fareMinor,
    String currency,
    UUID driverId,
    UUID vehicleId,
    UUID driverShiftId,
    RideStatus status,
    CancellationActor cancellationActor,
    String cancellationReason,
    Instant requestedAt,
    Instant assignedAt,
    Instant arrivedAt,
    Instant startedAt,
    Instant completedAt,
    Instant cancelledAt,
    Instant updatedAt,
    long version) {

  public Ride matching(Instant now) {
    return transition(RideStatus.REQUESTED, RideStatus.MATCHING, now, null, null, null);
  }

  public Ride noDriver(Instant now) {
    return transition(RideStatus.MATCHING, RideStatus.NO_DRIVER, now, null, null, null);
  }

  public Ride assign(UUID driver, UUID vehicle, UUID shift, Instant now) {
    require(RideStatus.MATCHING);
    return new Ride(id, riderAccountId, quoteId, productId, pickup, dropoff, fareMinor, currency,
        driver, vehicle, shift, RideStatus.DRIVER_ASSIGNED, null, null, requestedAt, now, null,
        null, null, null, now, version + 1);
  }

  public Ride arriving(Instant now) {
    return transition(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVING, now, null, null, null);
  }

  public Ride arrived(Instant now) {
    return transition(RideStatus.DRIVER_ARRIVING, RideStatus.DRIVER_ARRIVED, now, now, null, null);
  }

  public Ride start(Instant now) {
    return transition(RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS, now, arrivedAt, now, null);
  }

  public Ride complete(Instant now) {
    return transition(RideStatus.IN_PROGRESS, RideStatus.COMPLETED, now, arrivedAt, startedAt, now);
  }

  public Ride cancel(CancellationActor actor, String reason, Instant now) {
    if (reason == null || reason.isBlank()) {
      throw new InvalidRequestException("Cancellation reason is required");
    }
    if (status == RideStatus.COMPLETED || status == RideStatus.CANCELLED
        || status == RideStatus.NO_DRIVER) {
      throw new ConflictException("Ride cannot be cancelled from " + status);
    }
    if (actor == CancellationActor.RIDER && status == RideStatus.IN_PROGRESS) {
      throw new ConflictException("Rider cannot cancel a ride in progress");
    }
    if (actor == CancellationActor.DRIVER
        && status != RideStatus.DRIVER_ASSIGNED && status != RideStatus.DRIVER_ARRIVING
        && status != RideStatus.DRIVER_ARRIVED) {
      throw new ConflictException("Driver cannot cancel ride from " + status);
    }
    return new Ride(id, riderAccountId, quoteId, productId, pickup, dropoff, fareMinor, currency,
        driverId, vehicleId, driverShiftId, RideStatus.CANCELLED, actor, reason, requestedAt,
        assignedAt, arrivedAt, startedAt, completedAt, now, now, version + 1);
  }

  private Ride transition(
      RideStatus expected, RideStatus next, Instant now, Instant arrived, Instant started,
      Instant completed) {
    require(expected);
    return new Ride(id, riderAccountId, quoteId, productId, pickup, dropoff, fareMinor, currency,
        driverId, vehicleId, driverShiftId, next, null, null, requestedAt, assignedAt, arrived,
        started, completed, null, now, version + 1);
  }

  private void require(RideStatus expected) {
    if (status != expected) {
      throw new ConflictException("Ride must be " + expected + " but is " + status);
    }
  }
}

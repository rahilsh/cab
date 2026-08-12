package in.rsh.cab.fleet;

import in.rsh.cab.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;

public record DriverShift(
    UUID id,
    UUID driverId,
    UUID vehicleId,
    ShiftStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt,
    Instant availableAt,
    Instant closedAt) {

  public DriverShift goOnline(Instant now) {
    require(ShiftStatus.OFFLINE, "Only an offline shift can go online");
    return transition(ShiftStatus.AVAILABLE, now, now, null);
  }

  public DriverShift goOffline(Instant now) {
    require(ShiftStatus.AVAILABLE, "Only an available shift can go offline");
    return transition(ShiftStatus.OFFLINE, now, availableAt, null);
  }

  public DriverShift close(Instant now) {
    require(ShiftStatus.OFFLINE, "Only an offline shift can be closed");
    return transition(ShiftStatus.CLOSED, now, availableAt, now);
  }

  private void require(ShiftStatus expected, String message) {
    if (status != expected) {
      throw new ConflictException(message);
    }
  }

  private DriverShift transition(
      ShiftStatus next, Instant now, Instant nextAvailableAt, Instant nextClosedAt) {
    return new DriverShift(
        id, driverId, vehicleId, next, version + 1, createdAt, now, nextAvailableAt, nextClosedAt);
  }
}

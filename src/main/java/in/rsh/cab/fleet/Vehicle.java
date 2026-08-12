package in.rsh.cab.fleet;

import in.rsh.cab.exception.InvalidRequestException;
import java.time.Instant;
import java.util.UUID;

public record Vehicle(
    UUID id,
    String registration,
    String serviceClass,
    int capacity,
    VehicleStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  public Vehicle {
    if (capacity < 1 || capacity > 20) {
      throw new InvalidRequestException("Vehicle capacity must be between 1 and 20");
    }
  }

  public Vehicle update(
      String registration, String serviceClass, int capacity, VehicleStatus status, Instant now) {
    return new Vehicle(
        id, registration, serviceClass, capacity, status, version + 1, createdAt, now);
  }
}

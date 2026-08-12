package in.rsh.cab.driver;

import in.rsh.cab.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;

public record DriverProfile(
    UUID id,
    UUID accountId,
    String legalName,
    String phoneNumber,
    DriverStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public DriverProfile approve(Instant now) {
    if (status != DriverStatus.PENDING) {
      throw new ConflictException("Only a pending driver can be approved");
    }
    return new DriverProfile(
        id, accountId, legalName, phoneNumber, DriverStatus.APPROVED, createdAt, now);
  }

  public DriverProfile suspend(Instant now) {
    if (status != DriverStatus.APPROVED) {
      throw new ConflictException("Only an approved driver can be suspended");
    }
    return new DriverProfile(
        id, accountId, legalName, phoneNumber, DriverStatus.SUSPENDED, createdAt, now);
  }
}

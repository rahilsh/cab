package in.rsh.cab.location.internal.persistence;

import in.rsh.cab.location.DriverLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LocationCheckpointRepository {

  boolean insertIfNewer(UUID tenantId, DriverLocation location, Instant createdAt);

  List<DriverLocation> findLatestEligibleAfter(
      UUID tenantId, Instant recordedSince, LocalDate currentDate, UUID afterShiftId, int limit);

  int deleteCreatedBefore(UUID tenantId, Instant cutoff, int limit);
}

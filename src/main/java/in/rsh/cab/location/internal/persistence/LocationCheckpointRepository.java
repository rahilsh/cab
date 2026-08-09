package in.rsh.cab.location.internal.persistence;

import in.rsh.cab.location.DriverLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LocationCheckpointRepository {

  boolean insertIfNewer(UUID tenantId, DriverLocation location, Instant createdAt);

  List<DriverLocation> findLatestEligible(
      UUID tenantId, Instant recordedSince, LocalDate currentDate, int limit);
}

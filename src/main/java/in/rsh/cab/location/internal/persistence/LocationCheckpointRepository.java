package in.rsh.cab.location.internal.persistence;

import in.rsh.cab.location.DriverLocation;
import java.time.Instant;
import java.util.UUID;

public interface LocationCheckpointRepository {

  void insert(UUID tenantId, DriverLocation location, Instant createdAt);
}

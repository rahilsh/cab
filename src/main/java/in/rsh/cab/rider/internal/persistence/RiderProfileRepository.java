package in.rsh.cab.rider.internal.persistence;

import in.rsh.cab.rider.RiderProfile;
import java.util.Optional;
import java.util.UUID;

public interface RiderProfileRepository {

  Optional<RiderProfile> findByTenantIdAndAccountId(UUID tenantId, UUID accountId);

  void insert(UUID tenantId, UUID accountId, RiderProfile profile);

  boolean update(UUID tenantId, UUID accountId, String displayName, String phoneNumber,
      java.time.Instant updatedAt);
}

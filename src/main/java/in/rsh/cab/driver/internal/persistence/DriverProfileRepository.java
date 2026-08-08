package in.rsh.cab.driver.internal.persistence;

import in.rsh.cab.driver.DriverProfile;
import in.rsh.cab.driver.DriverStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverProfileRepository {

  void insert(UUID tenantId, DriverProfile profile);

  Optional<DriverProfile> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<DriverProfile> findByTenantIdAndAccountId(UUID tenantId, UUID accountId);

  List<DriverProfile> findAllByTenantId(UUID tenantId);

  boolean updateStatus(UUID tenantId, UUID id, DriverStatus expected, DriverStatus status,
      Instant updatedAt);

  boolean updateOwn(UUID tenantId, UUID accountId, String legalName, String phoneNumber,
      Instant updatedAt);
}

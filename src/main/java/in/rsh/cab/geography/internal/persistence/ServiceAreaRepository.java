package in.rsh.cab.geography.internal.persistence;

import in.rsh.cab.geography.ServiceArea;
import in.rsh.cab.geography.GeoPoint;
import java.util.List;
import java.util.UUID;

public interface ServiceAreaRepository {

  boolean existsByTenantIdAndSlugOrName(UUID tenantId, String slug, String name);

  void insert(UUID tenantId, ServiceArea serviceArea);

  List<ServiceArea> findAllByTenantId(UUID tenantId);

  boolean coversRoute(UUID tenantId, GeoPoint pickup, GeoPoint dropoff);
}

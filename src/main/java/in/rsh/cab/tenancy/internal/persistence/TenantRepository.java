package in.rsh.cab.tenancy.internal.persistence;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
  boolean existsBySlug(String slug);

  @Query("select tenant.id from TenantEntity tenant where tenant.status = 'ACTIVE' order by tenant.id")
  List<UUID> findActiveIds();
}

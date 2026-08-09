package in.rsh.cab.tenancy.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRepository extends JpaRepository<TenantMembershipEntity, UUID> {

  @EntityGraph(attributePaths = "roles")
  List<TenantMembershipEntity> findByUserAccountIdAndStatus(UUID userAccountId, String status);

  @EntityGraph(attributePaths = "roles")
  Optional<TenantMembershipEntity> findByTenantIdAndUserAccountIdAndStatus(
      UUID tenantId, UUID userAccountId, String status);

  @EntityGraph(attributePaths = "roles")
  Optional<TenantMembershipEntity> findByTenantIdAndUserAccountId(
      UUID tenantId, UUID userAccountId);
}

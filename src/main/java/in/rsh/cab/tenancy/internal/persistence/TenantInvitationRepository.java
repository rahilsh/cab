package in.rsh.cab.tenancy.internal.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TenantInvitationRepository extends JpaRepository<TenantInvitationEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = "roles")
  Optional<TenantInvitationEntity> findByTokenHash(String tokenHash);

  @EntityGraph(attributePaths = "roles")
  Optional<TenantInvitationEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}

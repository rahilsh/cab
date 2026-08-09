package in.rsh.cab.tenancy.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
  Optional<UserAccountEntity> findByIssuerAndSubject(String issuer, String subject);
}

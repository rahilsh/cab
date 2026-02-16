package in.rsh.cab.repository;

import in.rsh.cab.entity.CabEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface CabJpaRepository extends JpaRepository<CabEntity, Integer> {

  Optional<CabEntity> findById(int cabId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<CabEntity> findByCityId(Integer cityId);

  List<CabEntity> findByStatus(CabEntity.CabStatus status);
}

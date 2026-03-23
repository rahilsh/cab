package in.rsh.cab.repository;

import in.rsh.cab.entity.CabEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CabJpaRepository extends JpaRepository<CabEntity, Integer> {

  Optional<CabEntity> findById(int cabId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM CabEntity c WHERE c.cityId = :cityId AND c.status = 'AVAILABLE' ORDER BY c.id")
  List<CabEntity> findAvailableCabsInCityWithLock(@Param("cityId") Integer cityId);

  List<CabEntity> findByCityIdAndStatus(Integer cityId, CabEntity.CabStatus status);

  List<CabEntity> findByStatus(CabEntity.CabStatus status);

  Page<CabEntity> findAll(Pageable pageable);
}

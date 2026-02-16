package in.rsh.cab.commons.repository;

import in.rsh.cab.commons.entity.CabEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CabJpaRepository extends JpaRepository<CabEntity, Integer> {
    Optional<CabEntity> findByCabId(String cabId);
    List<CabEntity> findByCityId(Integer cityId);
    List<CabEntity> findByStatus(CabEntity.CabStatus status);
}

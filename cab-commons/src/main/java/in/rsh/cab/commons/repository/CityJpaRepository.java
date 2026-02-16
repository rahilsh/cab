package in.rsh.cab.commons.repository;

import in.rsh.cab.commons.entity.CityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityJpaRepository extends JpaRepository<CityEntity, Integer> {
    Optional<CityEntity> findByName(String name);
}

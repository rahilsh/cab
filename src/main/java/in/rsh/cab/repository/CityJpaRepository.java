package in.rsh.cab.repository;

import in.rsh.cab.entity.CityEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CityJpaRepository extends JpaRepository<CityEntity, Integer> {

  Optional<CityEntity> findByName(String name);
}

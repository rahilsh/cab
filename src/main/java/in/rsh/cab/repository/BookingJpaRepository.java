package in.rsh.cab.repository;

import in.rsh.cab.entity.BookingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingJpaRepository extends JpaRepository<BookingEntity, Integer> {

  Optional<BookingEntity> findByBookingId(String bookingId);

  Optional<BookingEntity> findByCabId(String cabId);
}

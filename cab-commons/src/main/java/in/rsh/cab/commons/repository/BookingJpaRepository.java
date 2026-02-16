package in.rsh.cab.commons.repository;

import in.rsh.cab.commons.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingJpaRepository extends JpaRepository<BookingEntity, Integer> {
    Optional<BookingEntity> findByBookingId(String bookingId);
    Optional<BookingEntity> findByCabId(String cabId);
}

package in.rsh.cab.template;

import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.repository.BookingJpaRepository;
import java.time.LocalDateTime;

public abstract class BookingTemplate {

  protected final BookingJpaRepository bookingJpaRepository;

  protected BookingTemplate(BookingJpaRepository bookingJpaRepository) {
    this.bookingJpaRepository = bookingJpaRepository;
  }

  public final Booking execute(String employeeId, Integer fromCity, Integer toCity) {
    validateRequest(employeeId, fromCity, toCity);
    validateCities(fromCity, toCity);
    Cab cab = reserveCab(fromCity, toCity);
    BookingEntity entity = buildBookingEntity(employeeId, fromCity, toCity, cab);
    BookingEntity saved = bookingJpaRepository.save(entity);
    postProcessing(saved);
    return mapToModel(saved);
  }

  protected abstract void validateRequest(String employeeId, Integer fromCity, Integer toCity);

  protected abstract void validateCities(Integer fromCity, Integer toCity);

  protected abstract Cab reserveCab(Integer fromCity, Integer toCity);

  protected abstract void postProcessing(BookingEntity entity);

  protected abstract Booking mapToModel(BookingEntity entity);

  protected BookingEntity buildBookingEntity(String employeeId, Integer fromCity, Integer toCity, Cab cab) {
    return BookingEntity.builder()
        .cabId(cab.getCabId())
        .bookedBy(employeeId)
        .startLocationX(fromCity)
        .startLocationY(toCity)
        .endLocationX(toCity)
        .endLocationY(toCity)
        .startTime(LocalDateTime.now())
        .status(BookingEntity.BookingStatus.IN_PROGRESS)
        .build();
  }
}

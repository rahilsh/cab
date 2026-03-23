package in.rsh.cab.service;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.entity.IdempotencyKeyEntity;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.repository.IdempotencyKeyJpaRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

  private final CabService cabService;
  private final BookingJpaRepository bookingJpaRepository;
  private final CityService cityService;
  private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

  @Autowired
  public BookingService(
      CabService cabService,
      BookingJpaRepository bookingJpaRepository,
      CityService cityService,
      IdempotencyKeyJpaRepository idempotencyKeyJpaRepository) {
    this.cabService = cabService;
    this.bookingJpaRepository = bookingJpaRepository;
    this.cityService = cityService;
    this.idempotencyKeyJpaRepository = idempotencyKeyJpaRepository;
  }

  public Booking bookCab(Integer employeeId, Integer fromCity, Integer toCity) {
    return createBooking(employeeId, fromCity, toCity);
  }

  @Transactional
  public Booking bookCab(
      Integer employeeId, Integer fromCity, Integer toCity, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return createBooking(employeeId, fromCity, toCity);
    }

    Optional<IdempotencyKeyEntity> existingKey = idempotencyKeyJpaRepository.findById(idempotencyKey);
    if (existingKey.isPresent()) {
      return bookingJpaRepository.findById(existingKey.get().getBookingId()).map(this::toModel).orElse(null);
    }

    Booking booking = createBooking(employeeId, fromCity, toCity);
    try {
      idempotencyKeyJpaRepository.save(new IdempotencyKeyEntity(idempotencyKey, booking.getBookingId()));
    } catch (Exception e) {
      try {
        bookingJpaRepository.deleteById(booking.getBookingId());
      } catch (Exception deleteEx) {
        throw new IllegalStateException("Failed to create booking and cleanup failed. Booking ID: " + booking.getBookingId(), deleteEx);
      }
      throw e;
    }
    return booking;
  }

  private Booking createBooking(Integer employeeId, Integer fromCity, Integer toCity) {
    validateCities(fromCity, toCity);
    Cab cab = cabService.reserveMostSuitableCab(fromCity, toCity);
    BookingEntity entity = BookingEntity.builder()
        .cabId(cab.getCabId())
        .bookedBy(String.valueOf(employeeId))
        .startLocationX(fromCity)
        .startLocationY(toCity)
        .endLocationX(toCity)
        .endLocationY(toCity)
        .startTime(LocalDateTime.now())
        .status(BookingEntity.BookingStatus.IN_PROGRESS)
        .build();
    BookingEntity saved = bookingJpaRepository.save(entity);
    return toModel(saved);
  }

  private void validateCities(Integer fromCity, Integer toCity) {
    cityService.validateCityOrThrow(fromCity);
    cityService.validateCityOrThrow(toCity);
    if (fromCity.equals(toCity)) {
      throw new InvalidRequestException("From and To city are same");
    }
  }

  public Collection<Booking> getAllBookings() {
    return bookingJpaRepository.findAll().stream().map(this::toModel).toList();
  }

  private Booking toModel(BookingEntity entity) {
    if (entity == null) {
      return null;
    }
    return Booking.builder()
        .bookingId(entity.getBookingId())
        .startTime(entity.getStartTime())
        .endTime(entity.getEndTime())
        .riderId(entity.getRiderId())
        .cabId(entity.getCabId())
        .status(
            entity.getStatus() != null ? Booking.BookingStatus.valueOf(entity.getStatus().name())
                : null)
        .startLocation(entity.getStartLocationX() != null && entity.getStartLocationY() != null
            ? new Location(entity.getStartLocationX(), entity.getStartLocationY()) : null)
        .endLocation(entity.getEndLocationX() != null && entity.getEndLocationY() != null
            ? new Location(entity.getEndLocationX(), entity.getEndLocationY()) : null)
        .bookedBy(entity.getBookedBy())
        .build();
  }
}

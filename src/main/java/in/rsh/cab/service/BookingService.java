package in.rsh.cab.service;

import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.entity.IdempotencyKeyEntity;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.mapper.BookingMapper;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.model.response.BookingResponse;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.repository.IdempotencyKeyJpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

  private final CabService cabService;
  private final BookingJpaRepository bookingJpaRepository;
  private final CityService cityService;
  private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
  private final BookingMapper bookingMapper;

  @Autowired
  public BookingService(
      CabService cabService,
      BookingJpaRepository bookingJpaRepository,
      CityService cityService,
      IdempotencyKeyJpaRepository idempotencyKeyJpaRepository,
      BookingMapper bookingMapper) {
    this.cabService = cabService;
    this.bookingJpaRepository = bookingJpaRepository;
    this.cityService = cityService;
    this.idempotencyKeyJpaRepository = idempotencyKeyJpaRepository;
    this.bookingMapper = bookingMapper;
  }

  @Transactional
  public Booking bookCab(
      String employeeId, Integer fromCity, Integer toCity, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return createBooking(employeeId, fromCity, toCity);
    }

    Optional<IdempotencyKeyEntity> existingKey = idempotencyKeyJpaRepository.findById(idempotencyKey);
    if (existingKey.isPresent()) {
      return bookingJpaRepository.findById(existingKey.get().getBookingId()).map(bookingMapper::toModel).orElse(null);
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

  private Booking createBooking(String employeeId, Integer fromCity, Integer toCity) {
    validateCities(fromCity, toCity);
    Cab cab = cabService.reserveMostSuitableCab(fromCity, toCity);
    BookingEntity entity = BookingEntity.builder()
        .cabId(cab.getCabId())
        .bookedBy(employeeId)
        .startLocationX(fromCity)
        .startLocationY(toCity)
        .endLocationX(toCity)
        .endLocationY(toCity)
        .startTime(LocalDateTime.now())
        .status(BookingEntity.BookingStatus.IN_PROGRESS)
        .build();
    BookingEntity saved = bookingJpaRepository.save(entity);
    return bookingMapper.toModel(saved);
  }

  private void validateCities(Integer fromCity, Integer toCity) {
    cityService.validateCityOrThrow(fromCity);
    cityService.validateCityOrThrow(toCity);
    if (fromCity.equals(toCity)) {
      throw new InvalidRequestException("From and To city are same");
    }
  }

  public Page<BookingResponse> getAllBookings(Pageable pageable) {
    return bookingJpaRepository.findAll(pageable)
        .map(bookingMapper::toModel)
        .map(bookingMapper::toResponse);
  }

  public BookingResponse bookCabResponse(
      String employeeId, Integer fromCity, Integer toCity, String idempotencyKey) {
    Booking booking = bookCab(employeeId, fromCity, toCity, idempotencyKey);
    return bookingMapper.toResponse(booking);
  }
}

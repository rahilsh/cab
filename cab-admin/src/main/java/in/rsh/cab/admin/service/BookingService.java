package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.InvalidRequestException;
import in.rsh.cab.commons.entity.BookingEntity;
import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Location;
import in.rsh.cab.commons.repository.BookingJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class BookingService {

  private final CabService cabService;
  private final BookingJpaRepository bookingJpaRepository;
  private final CityService cityService;
  private final AtomicInteger globalId = new AtomicInteger(0);
  private final Map<String, BookingRecord> records = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Autowired
  public BookingService(
      CabService cabService,
      BookingJpaRepository bookingJpaRepository,
      CityService cityService) {
    this.cabService = cabService;
    this.bookingJpaRepository = bookingJpaRepository;
    this.cityService = cityService;
  }

  public Booking bookCab(Integer employeeId, Integer fromCity, Integer toCity) {
    return createBooking(employeeId, fromCity, toCity);
  }

  public Booking bookCab(
      Integer employeeId, Integer fromCity, Integer toCity, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return createBooking(employeeId, fromCity, toCity);
    }
    BookingRequest request = new BookingRequest(employeeId, fromCity, toCity);
    return withIdempotency(
        idempotencyKey, request, () -> createBooking(employeeId, fromCity, toCity));
  }

  private Booking createBooking(Integer employeeId, Integer fromCity, Integer toCity) {
    validateCities(fromCity, toCity);
    Cab cab = cabService.reserveMostSuitableCab(fromCity, toCity);
    return addBooking(Integer.valueOf(cab.getCabId()), employeeId, fromCity, toCity);
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
    if (entity == null) return null;
    return Booking.builder()
        .bookingId(entity.getBookingId())
        .startTime(entity.getStartTime())
        .endTime(entity.getEndTime())
        .riderId(entity.getRiderId())
        .cabId(entity.getCabId())
        .status(entity.getStatus() != null ? Booking.BookingStatus.valueOf(entity.getStatus().name()) : null)
        .startLocation(entity.getStartLocationX() != null && entity.getStartLocationY() != null
            ? new Location(entity.getStartLocationX(), entity.getStartLocationY()) : null)
        .endLocation(entity.getEndLocationX() != null && entity.getEndLocationY() != null
            ? new Location(entity.getEndLocationX(), entity.getEndLocationY()) : null)
        .bookedBy(entity.getBookedBy())
        .build();
  }

  private Booking addBooking(Integer cabId, Integer employeeId, Integer fromCity, Integer toCity) {
    int bookingId = globalId.incrementAndGet();
    BookingEntity entity = BookingEntity.builder()
        .bookingId(String.valueOf(bookingId))
        .cabId(String.valueOf(cabId))
        .bookedBy(String.valueOf(employeeId))
        .startLocationX(fromCity)
        .startLocationY(fromCity)
        .endLocationX(toCity)
        .endLocationY(toCity)
        .startTime(LocalDateTime.now())
        .status(BookingEntity.BookingStatus.IN_PROGRESS)
        .build();
    BookingEntity saved = bookingJpaRepository.save(entity);
    return toModel(saved);
  }

  private Booking getBookingByCabId(Integer cabId) {
    return bookingJpaRepository.findByCabId(String.valueOf(cabId)).map(this::toModel).orElse(null);
  }

  public Booking withIdempotency(
      String key, BookingRequest request, Supplier<Booking> bookingSupplier) {
    ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
    lock.lock();
    try {
      BookingRecord existing = records.get(key);
      if (existing != null) {
        if (!existing.request().equals(request)) {
          throw new IllegalArgumentException("Idempotency key already used for a different request");
        }
        return existing.booking();
      }
      Booking booking = bookingSupplier.get();
      records.put(key, new BookingRecord(booking, request));
      return booking;
    } finally {
      lock.unlock();
    }
  }

  public record BookingRequest(Integer employeeId, Integer fromCity, Integer toCity) {}

  public record BookingRecord(Booking booking, BookingRequest request) {}
}

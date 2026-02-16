package in.rsh.cab.service;

import in.rsh.cab.exception.CabNotAvailableException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.model.Rider;
import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.entity.IdempotencyKeyEntity;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.repository.IdempotencyKeyJpaRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

  public static final double MAX_DISTANCE_DRIVER_CAN_TRAVEL = 10.0;
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
    idempotencyKeyJpaRepository.save(new IdempotencyKeyEntity(idempotencyKey, booking.getBookingId()));
    return booking;
  }

  private Booking createBooking(Integer employeeId, Integer fromCity, Integer toCity) {
    validateCities(fromCity, toCity);
    Cab cab = cabService.reserveMostSuitableCab(fromCity, toCity);
    BookingEntity entity = BookingEntity.builder()
        .cabId(cab.getCabId())
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

  @Transactional
  public Booking bookCab(Rider rider) {
    Optional<Cab> cabOptional =
        cabService.getAllCabs().stream()
            .filter(cab -> cab.getStatus().equals(Cab.CabStatus.AVAILABLE))
            .min((c1, c2) -> getNearestCab(c1.getLocation(), c2.getLocation()));
    if (cabOptional.isPresent()) {
      if (isDistanceMoreThanThreshold(
          rider.getCurrentLocation(), cabOptional.get().getLocation())) {
        throw new CabNotAvailableException();
      }
      Cab cab = cabOptional.get();
      cabService.updateCabStatus(cab.getCabId(), Cab.CabStatus.ON_RIDE);

      BookingEntity booking =
          BookingEntity.builder()
              .cabId(cab.getCabId())
              .riderId(rider.getPersonId())
              .startTime(LocalDateTime.now())
              .status(BookingEntity.BookingStatus.IN_PROGRESS)
              .build();
      BookingEntity saved = bookingJpaRepository.save(booking);
      return toModel(saved);
    }
    throw new CabNotAvailableException();
  }

  private boolean isDistanceMoreThanThreshold(Location currentLocation, Location location) {
    return (Math.sqrt(
        Math.pow(currentLocation.latitude() - location.latitude(), 2)
            + Math.pow(currentLocation.longitude() - location.longitude(), 2)))
        > MAX_DISTANCE_DRIVER_CAN_TRAVEL;
  }

  private int getNearestCab(Location location, Location location1) {
    return ((location.latitude() + location.longitude())
        - (location1.latitude() + location1.longitude()));
  }

  public List<Booking> getBookingsForRider(Rider rider) {
    return bookingJpaRepository.findAll().stream()
        .filter(booking -> booking.getRiderId().equals(rider.getPersonId()))
        .map(this::toModel)
        .collect(Collectors.toList());
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

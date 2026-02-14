package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.InvalidRequestException;
import in.rsh.cab.admin.store.BookingStore;
import in.rsh.cab.admin.store.IdempotencyStore;
import in.rsh.cab.admin.store.IdempotencyStore.BookingRequest;
import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class BookingService {

  private final CabService cabService;
  private final BookingStore bookingStore;
  private final CityService cityService;
  private final IdempotencyStore idempotencyStore;

  @Autowired
  public BookingService(
      CabService cabService,
      BookingStore bookingStore,
      CityService cityService,
      IdempotencyStore idempotencyStore) {
    this.cabService = cabService;
    this.bookingStore = bookingStore;
    this.cityService = cityService;
    this.idempotencyStore = idempotencyStore;
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
    return idempotencyStore.withIdempotency(
        idempotencyKey, request, () -> createBooking(employeeId, fromCity, toCity));
  }

  private Booking createBooking(Integer employeeId, Integer fromCity, Integer toCity) {
    validateCities(fromCity, toCity);
    Cab cab = cabService.reserveMostSuitableCab(fromCity, toCity);
    bookingStore.addBooking(Integer.valueOf(cab.getCabId()), employeeId, fromCity, toCity);
    return bookingStore.getBookingByCabId(Integer.valueOf(cab.getCabId()));
  }

  private void validateCities(Integer fromCity, Integer toCity) {
    cityService.validateCityOrThrow(fromCity);
    cityService.validateCityOrThrow(toCity);
    if (fromCity.equals(toCity)) {
      throw new InvalidRequestException("From and To city are same");
    }
  }

  public Collection<Booking> getAllBookings() {
    return bookingStore.bookings();
  }
}

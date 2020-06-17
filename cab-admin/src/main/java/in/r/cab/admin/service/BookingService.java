package in.r.cab.admin.service;

import in.r.cab.admin.model.Booking;
import in.r.cab.admin.model.Cab;
import in.r.cab.admin.model.Cab.State;
import in.r.cab.admin.store.BookingStore;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

  private final CabsService cabsService;
  private final BookingStore bookingStore;
  private final CityService cityService;

  @Autowired
  public BookingService(
      CabsService cabsService, BookingStore bookingStore, CityService cityService) {
    this.cabsService = cabsService;
    this.bookingStore = bookingStore;
    this.cityService = cityService;
  }

  public Booking bookCab(Integer employeeId, Integer fromCity, Integer toCity) {
    // checkIfEmployeeExists()
    // checkIfEmployeeIsAlreadyOnARide()
    validateCities(fromCity, toCity);
    Cab cab = cabsService.getMostSuitableCab(fromCity);
    cabsService.update(cab.getCabId(), toCity, State.ON_TRIP);
    Booking booking = bookingStore.addBooking(cab.getCabId(), employeeId, fromCity, toCity);
    cabsService.update(cab.getCabId(), State.IDLE, System.currentTimeMillis());
    return booking;
  }

  private void validateCities(Integer fromCity, Integer toCity) {
    cityService.validateCityOrThrow(toCity);
    if (fromCity.equals(toCity)) {
      throw new RuntimeException("From and To city are same");
    }
  }

  public Collection<Booking> getAllBookings() {
    return bookingStore.bookings();
  }
}

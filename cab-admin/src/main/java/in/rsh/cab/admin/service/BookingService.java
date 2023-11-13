package in.rsh.cab.admin.service;

import in.rsh.cab.admin.store.BookingStore;
import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

  private final CabService cabService;
  private final BookingStore bookingStore;
  private final CityService cityService;

  @Autowired
  public BookingService(CabService cabService, BookingStore bookingStore, CityService cityService) {
    this.cabService = cabService;
    this.bookingStore = bookingStore;
    this.cityService = cityService;
  }

  public Booking bookCab(Integer employeeId, Integer fromCity, Integer toCity) {
    // checkIfEmployeeExists()
    // checkIfEmployeeIsAlreadyOnARide()
    validateCities(fromCity, toCity);
    Cab cab = cabService.getMostSuitableCab(fromCity);
    cabService.update(Integer.valueOf(cab.getCabId()), toCity, Cab.CabStatus.UNAVAILABLE);
    Booking booking =
        bookingStore.addBooking(Integer.valueOf(cab.getCabId()), employeeId, fromCity, toCity);
    cabService.update(
        Integer.valueOf(cab.getCabId()), Cab.CabStatus.UNAVAILABLE, System.currentTimeMillis());
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

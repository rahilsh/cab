package in.rahilsh.cab.admin.store;

import in.rahilsh.cab.admin.model.Booking;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BookingStore {

  static int globalId = 0;
  static Map<Integer, Booking> bookings = new HashMap<>();

  public Booking addBooking(Integer cabId, Integer employeeId, Integer fromCity, Integer toCity) {
    int bookingId = ++globalId;
    Booking booking =
        new Booking(bookingId, cabId, employeeId, fromCity, toCity, System.currentTimeMillis());
    bookings.put(bookingId, booking);
    return booking;
  }

  public Collection<Booking> bookings() {
    return bookings.values();
  }
}

package in.r.cab.admin.store;

import in.r.cab.admin.model.Booking;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BookingStore {

  private static int globalId = 0;
  private static final Map<Integer, Booking> bookings = new HashMap<>();

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

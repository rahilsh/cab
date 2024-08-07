package in.rsh.cab.admin.store;

import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Location;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class BookingStore {

  private static final Map<Integer, Booking> bookings = new HashMap<>();
  private static int globalId = 0;

  public Booking addBooking(Integer cabId, Integer employeeId, Integer fromCity, Integer toCity) {
    int bookingId = ++globalId;
    Booking booking =
        Booking.builder()
            .bookingId(String.valueOf(bookingId))
            .cabId(String.valueOf(cabId))
            .bookedBy(String.valueOf(employeeId))
            .startLocation(new Location(fromCity, fromCity))
            .endLocation(new Location(toCity, toCity))
            .startTime(LocalDateTime.now())
            .build();
    bookings.put(bookingId, booking);
    return booking;
  }

  public Collection<Booking> bookings() {
    return bookings.values();
  }
}

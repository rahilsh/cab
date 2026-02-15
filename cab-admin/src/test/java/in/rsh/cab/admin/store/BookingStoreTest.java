package in.rsh.cab.admin.store;

import in.rsh.cab.commons.model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class BookingStoreTest {

  private BookingStore bookingStore;

  @BeforeEach
  void setUp() {
    bookingStore = new BookingStore();
  }

  @Nested
  class AddBookingTests {

    @Test
    void addBooking_shouldCreateBooking() {
      Booking booking = bookingStore.addBooking(1, 1, 1, 2);
      
      assertNotNull(booking);
      assertEquals("1", booking.getBookingId());
      assertEquals("1", booking.getCabId());
      assertEquals("1", booking.getBookedBy());
    }

    @Test
    void addBooking_multipleBookings_shouldAssignUniqueIds() {
      bookingStore.addBooking(1, 1, 1, 2);
      bookingStore.addBooking(1, 1, 1, 2);
      bookingStore.addBooking(1, 1, 1, 2);
      
      var bookings = bookingStore.bookings();
      assertEquals(3, bookings.size());
    }
  }

  @Nested
  class GetBookingsTests {

    @Test
    void bookings_shouldReturnAllBookings() {
      bookingStore.addBooking(1, 1, 1, 2);
      bookingStore.addBooking(1, 1, 1, 2);
      
      Collection<Booking> bookings = bookingStore.bookings();
      
      assertEquals(2, bookings.size());
    }

    @Test
    void bookings_emptyStore_shouldReturnEmptyCollection() {
      Collection<Booking> bookings = bookingStore.bookings();
      
      assertTrue(bookings.isEmpty());
    }
  }

  @Nested
  class GetBookingByCabIdTests {

    @Test
    void getBookingByCabId_withValidCabId_shouldReturnBooking() {
      bookingStore.addBooking(1, 1, 1, 2);
      
      Booking booking = bookingStore.getBookingByCabId(1);
      
      assertNotNull(booking);
      assertEquals("1", booking.getCabId());
    }

    @Test
    void getBookingByCabId_withInvalidCabId_shouldReturnNull() {
      Booking booking = bookingStore.getBookingByCabId(999);
      
      assertNull(booking);
    }
  }
}

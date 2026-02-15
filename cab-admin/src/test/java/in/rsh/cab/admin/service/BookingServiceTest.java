package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.InvalidRequestException;
import in.rsh.cab.admin.store.BookingStore;
import in.rsh.cab.admin.store.CabStore;
import in.rsh.cab.admin.store.IdempotencyStore;
import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {

  @Mock
  private CabService cabService;

  @Mock
  private BookingStore bookingStore;

  @Mock
  private CityService cityService;

  @Mock
  private IdempotencyStore idempotencyStore;

  @InjectMocks
  private BookingService bookingService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  class BookCabTests {

    @Test
    void bookCab_shouldCreateBooking() {
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);
      
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(cab);
      
      Booking booking = Booking.builder()
          .bookingId("1")
          .cabId("1")
          .build();
      when(bookingStore.addBooking(1, 1, 1, 2)).thenReturn(booking);
      when(bookingStore.getBookingByCabId(1)).thenReturn(booking);
      
      Booking result = bookingService.bookCab(1, 1, 2);
      
      assertNotNull(result);
      verify(bookingStore).addBooking(1, 1, 1, 2);
    }

    @Test
    void bookCab_withSameFromAndToCity_shouldThrowException() {
      doNothing().when(cityService).validateCityOrThrow(1);
      
      assertThrows(InvalidRequestException.class,
          () -> bookingService.bookCab(1, 1, 1));
    }

    @Test
    void bookCab_withIdempotencyKey_shouldUseIdempotencyStore() {
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);
      
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(cab);
      
      Booking booking = Booking.builder()
          .bookingId("1")
          .cabId("1")
          .build();
      when(bookingStore.addBooking(1, 1, 1, 2)).thenReturn(booking);
      when(bookingStore.getBookingByCabId(1)).thenReturn(booking);
      
      IdempotencyStore.BookingRequest request = 
          new IdempotencyStore.BookingRequest(1, 1, 2);
      when(idempotencyStore.withIdempotency(eq("key1"), eq(request), any()))
          .thenReturn(booking);
      
      Booking result = bookingService.bookCab(1, 1, 2, "key1");
      
      assertNotNull(result);
      verify(idempotencyStore).withIdempotency(eq("key1"), eq(request), any());
    }

    @Test
    void bookCab_withBlankIdempotencyKey_shouldNotUseIdempotencyStore() {
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);
      
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(cab);
      
      Booking booking = Booking.builder()
          .bookingId("1")
          .cabId("1")
          .build();
      when(bookingStore.addBooking(1, 1, 1, 2)).thenReturn(booking);
      when(bookingStore.getBookingByCabId(1)).thenReturn(booking);
      
      Booking result = bookingService.bookCab(1, 1, 2, "   ");
      
      assertNotNull(result);
      verify(idempotencyStore, never()).withIdempotency(any(), any(), any());
    }
  }

  @Nested
  class GetAllBookingsTests {

    @Test
    void getAllBookings_shouldReturnAllBookings() {
      when(bookingStore.bookings()).thenReturn(List.of());
      
      var bookings = bookingService.getAllBookings();
      
      assertNotNull(bookings);
      verify(bookingStore).bookings();
    }
  }
}

package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.InvalidRequestException;
import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.repository.BookingJpaRepository;
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
  private BookingJpaRepository bookingJpaRepository;

  @Mock
  private CityService cityService;

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
      
      when(bookingJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      
      Booking result = bookingService.bookCab(1, 1, 2);
      
      assertNotNull(result);
      verify(bookingJpaRepository).save(any());
    }

    @Test
    void bookCab_withSameFromAndToCity_shouldThrowException() {
      doNothing().when(cityService).validateCityOrThrow(1);
      
      assertThrows(InvalidRequestException.class,
          () -> bookingService.bookCab(1, 1, 1));
    }

    @Test
    void bookCab_withIdempotencyKey_shouldReturnSameBookingForSameKey() {
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);
      
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(cab);
      
      when(bookingJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      
      Booking result1 = bookingService.bookCab(1, 1, 2, "key1");
      Booking result2 = bookingService.bookCab(1, 1, 2, "key1");
      
      assertSame(result1, result2);
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
      
      when(bookingJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      
      Booking result = bookingService.bookCab(1, 1, 2, "   ");
      
      assertNotNull(result);
      verify(bookingJpaRepository, times(1)).save(any());
    }
  }

  @Nested
  class GetAllBookingsTests {

    @Test
    void getAllBookings_shouldReturnAllBookings() {
      when(bookingJpaRepository.findAll()).thenReturn(List.of());
      
      var bookings = bookingService.getAllBookings();
      
      assertNotNull(bookings);
      verify(bookingJpaRepository).findAll();
    }
  }
}

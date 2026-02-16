package in.rsh.cab.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.entity.IdempotencyKeyEntity;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.repository.IdempotencyKeyJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class BookingServiceTest {

  @Mock
  private CabService cabService;

  @Mock
  private BookingJpaRepository bookingJpaRepository;

  @Mock
  private CityService cityService;

  @Mock
  private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

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
          .cabId(1)
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(cab);

      when(bookingJpaRepository.save(any())).thenAnswer(i -> {
        BookingEntity entity = i.getArgument(0);
        entity.setBookingId(1);
        return entity;
      });

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
          .cabId(1)
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(cab);

      when(bookingJpaRepository.save(any())).thenAnswer(i -> {
        BookingEntity entity = i.getArgument(0);
        entity.setBookingId(1);
        return entity;
      });
      when(idempotencyKeyJpaRepository.findById("key1")).thenReturn(Optional.empty())
          .thenReturn(Optional.of(new IdempotencyKeyEntity("key1", 1)));
      when(bookingJpaRepository.findById(1)).thenReturn(Optional.of(new BookingEntity()));

      Booking result1 = bookingService.bookCab(1, 1, 2, "key1");
      Booking result2 = bookingService.bookCab(1, 1, 2, "key1");

      assertNotNull(result1);
      assertNotNull(result2);
      verify(bookingJpaRepository, times(1)).save(any());
    }

    @Test
    void bookCab_withBlankIdempotencyKey_shouldNotUseIdempotencyStore() {
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);

      Cab cab = Cab.builder()
          .cabId(1)
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(cab);

      when(bookingJpaRepository.save(any())).thenAnswer(i -> {
        BookingEntity entity = i.getArgument(0);
        entity.setBookingId(1);
        return entity;
      });

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

package in.rsh.cab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.entity.IdempotencyKeyEntity;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.response.BookingResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    void bookCab_withoutIdempotencyKey_shouldCreateBooking() {
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

      Booking result = bookingService.bookCab("emp1", 1, 2, null);

      assertNotNull(result);
      verify(bookingJpaRepository).save(any());
    }

    @Test
    void bookCab_withBlankIdempotencyKey_shouldCreateNewBooking() {
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

      Booking result = bookingService.bookCab("emp1", 1, 2, "   ");

      assertNotNull(result);
      verify(bookingJpaRepository, times(1)).save(any());
    }

    @Test
    void bookCab_withSameFromAndToCity_shouldThrowException() {
      doNothing().when(cityService).validateCityOrThrow(1);

      assertThrows(InvalidRequestException.class,
          () -> bookingService.bookCab("emp1", 1, 1, null));
    }

    @Test
    void bookCab_withExistingIdempotencyKey_shouldReturnExistingBooking() {
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

      IdempotencyKeyEntity idempotencyKeyEntity = new IdempotencyKeyEntity();
      idempotencyKeyEntity.setKeyValue("key1");
      idempotencyKeyEntity.setBookingId(1);

      when(idempotencyKeyJpaRepository.findById("key1")).thenReturn(Optional.of(idempotencyKeyEntity));

      BookingEntity existingBooking = new BookingEntity();
      existingBooking.setBookingId(1);
      existingBooking.setBookedBy("emp1");
      when(bookingJpaRepository.findById(1)).thenReturn(Optional.of(existingBooking));

      Booking result = bookingService.bookCab("emp1", 1, 2, "key1");

      assertNotNull(result);
      verify(cabService, times(0)).reserveMostSuitableCab(1, 2);
    }

    @Test
    void bookCab_withIdempotencyKey_whenKeySaveFails_shouldRollbackBooking() {
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
      when(idempotencyKeyJpaRepository.findById("fail-key")).thenReturn(Optional.empty());
      doThrow(new RuntimeException("DB error"))
          .when(idempotencyKeyJpaRepository).save(any(IdempotencyKeyEntity.class));

      assertThrows(RuntimeException.class,
          () -> bookingService.bookCab("emp1", 1, 2, "fail-key"));

      verify(bookingJpaRepository).deleteById(1);
    }

    @Test
    void bookCab_withIdempotencyKey_whenKeySaveAndDeleteFail_shouldThrowIllegalStateException() {
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
      when(idempotencyKeyJpaRepository.findById("fail-key")).thenReturn(Optional.empty());
      doThrow(new RuntimeException("DB error"))
          .when(idempotencyKeyJpaRepository).save(any(IdempotencyKeyEntity.class));
      doThrow(new RuntimeException("Delete failed"))
          .when(bookingJpaRepository).deleteById(1);

      assertThrows(IllegalStateException.class,
          () -> bookingService.bookCab("emp1", 1, 2, "fail-key"));
    }
  }

  @Nested
  class BookCabResponseTests {

    @Test
    void bookCabResponse_shouldReturnBookingResponse() {
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

      BookingResponse result = bookingService.bookCabResponse("emp1", 1, 2, null);

      assertNotNull(result);
      assertEquals(1, result.bookingId());
    }
  }

  @Nested
  class GetAllBookingsTests {

    @Test
    void getAllBookings_shouldReturnPagedResults() {
      Pageable pageable = PageRequest.of(0, 20);
      Page<BookingEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
      when(bookingJpaRepository.findAll(pageable)).thenReturn(emptyPage);

      Page<BookingResponse> result = bookingService.getAllBookings(pageable);

      assertNotNull(result);
      assertEquals(0, result.getTotalElements());
      verify(bookingJpaRepository).findAll(pageable);
    }

    @Test
    void getAllBookings_shouldMapEntitiesToResponse() {
      Pageable pageable = PageRequest.of(0, 20);
      BookingEntity entity = new BookingEntity();
      entity.setBookingId(1);
      entity.setBookedBy("emp1");
      Page<BookingEntity> page = new PageImpl<>(List.of(entity), pageable, 1);
      when(bookingJpaRepository.findAll(pageable)).thenReturn(page);

      Page<BookingResponse> result = bookingService.getAllBookings(pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(1, result.getContent().get(0).bookingId());
    }
  }
}

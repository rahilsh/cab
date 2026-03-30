package in.rsh.cab.template;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import in.rsh.cab.chain.ValidationChain;
import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.mapper.BookingMapper;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.service.CabService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class BookingTemplateTest {

  @Mock
  private BookingJpaRepository bookingJpaRepository;

  @Mock
  private CabService cabService;

  @Mock
  private BookingMapper bookingMapper;

  @Mock
  private ValidationChain validationChain;

  @InjectMocks
  private StandardBookingTemplate bookingTemplate;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  class ExecuteTests {

    @Test
    void execute_withValidRequest_shouldCreateBooking() {
      Cab mockCab = Cab.builder().cabId(1).status(Cab.CabStatus.AVAILABLE).build();
      Booking mockBooking = Booking.builder().bookingId(1).build();
      
      when(cabService.reserveMostSuitableCab(1, 2)).thenReturn(mockCab);
      when(bookingJpaRepository.save(any())).thenAnswer(i -> {
        BookingEntity e = i.getArgument(0);
        e.setBookingId(1);
        return e;
      });
      when(bookingMapper.toModel(any())).thenReturn(mockBooking);

      Booking result = bookingTemplate.execute("emp1", 1, 2);

      assertNotNull(result);
    }

    @Test
    void execute_withValidationFailure_shouldThrowException() {
      doThrow(new InvalidRequestException("Validation failed"))
          .when(validationChain).validate(any());

      assertThrows(InvalidRequestException.class,
          () -> bookingTemplate.execute("emp1", 1, 2));
    }

    @Test
    void execute_withNoAvailableCabs_shouldPropagateException() {
      when(cabService.reserveMostSuitableCab(1, 2))
          .thenThrow(new in.rsh.cab.exception.CabNotAvailableException("No cabs"));

      assertThrows(in.rsh.cab.exception.CabNotAvailableException.class,
          () -> bookingTemplate.execute("emp1", 1, 2));
    }
  }
}

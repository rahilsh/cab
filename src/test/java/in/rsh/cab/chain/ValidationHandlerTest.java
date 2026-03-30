package in.rsh.cab.chain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.model.BookingRequest;
import in.rsh.cab.service.CityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ValidationHandlerTest {

  @Mock
  private CityService cityService;

  @InjectMocks
  private EmployeeIdValidationHandler employeeIdValidationHandler;

  @InjectMocks
  private CityValidationHandler cityValidationHandler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  class EmployeeIdValidationHandlerTests {

    @Test
    void validate_withValidEmployeeId_shouldProceedToNext() {
      BookingRequest request = new BookingRequest("emp1", 1, 2, null);
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);
      employeeIdValidationHandler.setNext(cityValidationHandler);

      assertDoesNotThrow(() -> employeeIdValidationHandler.validate(request));
    }

    @Test
    void validate_withNullEmployeeId_shouldThrowException() {
      BookingRequest request = new BookingRequest(null, 1, 2, null);

      assertThrows(InvalidRequestException.class,
          () -> employeeIdValidationHandler.validate(request));
    }

    @Test
    void validate_withBlankEmployeeId_shouldThrowException() {
      BookingRequest request = new BookingRequest("   ", 1, 2, null);

      assertThrows(InvalidRequestException.class,
          () -> employeeIdValidationHandler.validate(request));
    }
  }

  @Nested
  class CityValidationHandlerTests {

    @Test
    void validate_withValidCities_shouldProceedToNext() {
      BookingRequest request = new BookingRequest("emp1", 1, 2, null);
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);

      assertDoesNotThrow(() -> cityValidationHandler.validate(request));
    }

    @Test
    void validate_withSameCities_shouldThrowException() {
      BookingRequest request = new BookingRequest("emp1", 1, 1, null);

      assertThrows(InvalidRequestException.class,
          () -> cityValidationHandler.validate(request));
    }

    @Test
    void validate_withNullCities_shouldThrowException() {
      BookingRequest request = new BookingRequest("emp1", null, null, null);

      assertThrows(InvalidRequestException.class,
          () -> cityValidationHandler.validate(request));
    }

    @Test
    void validate_withInvalidFromCity_shouldThrowException() {
      BookingRequest request = new BookingRequest("emp1", 999, 2, null);
      doThrow(new NotFoundException("City does not exist"))
          .when(cityService).validateCityOrThrow(999);

      assertThrows(NotFoundException.class,
          () -> cityValidationHandler.validate(request));
    }

    @Test
    void validate_withInvalidToCity_shouldThrowException() {
      BookingRequest request = new BookingRequest("emp1", 1, 999, null);
      doNothing().when(cityService).validateCityOrThrow(1);
      doThrow(new NotFoundException("City does not exist"))
          .when(cityService).validateCityOrThrow(999);

      assertThrows(NotFoundException.class,
          () -> cityValidationHandler.validate(request));
    }
  }

  @Nested
  class ValidationChainTests {

    @Test
    void validate_withValidRequest_shouldPass() {
      ValidationChain chain = new ValidationChain(
          employeeIdValidationHandler, cityValidationHandler);
      BookingRequest request = new BookingRequest("emp1", 1, 2, null);
      doNothing().when(cityService).validateCityOrThrow(1);
      doNothing().when(cityService).validateCityOrThrow(2);

      assertDoesNotThrow(() -> chain.validate(request));
    }

    @Test
    void validate_withInvalidEmployeeId_shouldThrowException() {
      ValidationChain chain = new ValidationChain(
          employeeIdValidationHandler, cityValidationHandler);
      BookingRequest request = new BookingRequest(null, 1, 2, null);

      assertThrows(InvalidRequestException.class,
          () -> chain.validate(request));
    }

    @Test
    void validate_withSameCity_shouldThrowException() {
      ValidationChain chain = new ValidationChain(
          employeeIdValidationHandler, cityValidationHandler);
      BookingRequest request = new BookingRequest("emp1", 1, 1, null);

      assertThrows(InvalidRequestException.class,
          () -> chain.validate(request));
    }
  }
}

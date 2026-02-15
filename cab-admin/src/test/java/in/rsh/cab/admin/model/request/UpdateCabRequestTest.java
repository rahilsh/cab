package in.rsh.cab.admin.model.request;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCabRequestTest {

  @Nested
  class ValidateTests {

    @Test
    void validate_withValidStateAndCityId_shouldNotThrow() {
      UpdateCabRequest request = new UpdateCabRequest("AVAILABLE", 1);
      
      assertDoesNotThrow(request::validate);
    }

    @Test
    void validate_withStateOnly_shouldNotThrow() {
      UpdateCabRequest request = new UpdateCabRequest("AVAILABLE", null);
      
      assertDoesNotThrow(request::validate);
    }

    @Test
    void validate_withCityIdOnly_shouldNotThrow() {
      UpdateCabRequest request = new UpdateCabRequest(null, 1);
      
      assertDoesNotThrow(request::validate);
    }

    @Test
    void validate_withNullStateAndNullCityId_shouldThrowException() {
      UpdateCabRequest request = new UpdateCabRequest(null, null);
      
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          request::validate
      );
      assertEquals("Invalid Params", exception.getMessage());
    }

    @Test
    void validate_withOnRideState_shouldThrowException() {
      UpdateCabRequest request = new UpdateCabRequest("ON_RIDE", 1);
      
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          request::validate
      );
      assertEquals("Invalid State Transition", exception.getMessage());
    }

    @Test
    void validate_withInvalidState_shouldThrowException() {
      UpdateCabRequest request = new UpdateCabRequest("INVALID_STATE", 1);
      
      assertThrows(IllegalArgumentException.class, request::validate);
    }
  }
}

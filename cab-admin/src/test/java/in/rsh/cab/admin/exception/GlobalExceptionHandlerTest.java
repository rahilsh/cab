package in.rsh.cab.admin.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Nested
  class HandleNotFoundTests {

    @Test
    void handleNotFound_shouldReturnNotFoundStatus() {
      NotFoundException exception = new NotFoundException("City not found");
      
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
          handler.handleNotFound(exception);
      
      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      assertEquals("City not found", response.getBody().message());
    }
  }

  @Nested
  class HandleCabNotAvailableTests {

    @Test
    void handleCabNotAvailable_shouldReturnConflictStatus() {
      CabNotAvailableException exception = new CabNotAvailableException("No cabs available");
      
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
          handler.handleCabNotAvailable(exception);
      
      assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
      assertEquals("No cabs available", response.getBody().message());
    }
  }

  @Nested
  class HandleBadRequestTests {

    @Test
    void handleBadRequest_shouldReturnBadRequestStatus() {
      InvalidRequestException exception = new InvalidRequestException("Invalid request");
      
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
          handler.handleBadRequest(exception);
      
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertEquals("Invalid request", response.getBody().message());
    }

    @Test
    void handleBadRequest_withIllegalArgument_shouldReturnBadRequestStatus() {
      IllegalArgumentException exception = new IllegalArgumentException("Bad argument");
      
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
          handler.handleBadRequest(exception);
      
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertEquals("Bad argument", response.getBody().message());
    }
  }

  @Nested
  class HandleUnexpectedTests {

    @Test
    void handleUnexpected_shouldReturnInternalServerErrorStatus() {
      Exception exception = new RuntimeException("Unexpected error");
      
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
          handler.handleUnexpected(exception);
      
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
      assertEquals("Unexpected error", response.getBody().message());
    }
  }
}

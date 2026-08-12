package in.rsh.cab.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.routing.RouteProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  void mapsKnownApplicationErrors() {
    assertProblem(
        handler.handleNotFound(new NotFoundException("City not found")),
        HttpStatus.NOT_FOUND,
        "not-found",
        "City not found");
    assertProblem(
        handler.handleCabNotAvailable(new CabNotAvailableException("No cabs available")),
        HttpStatus.CONFLICT,
        "cab-unavailable",
        "No cabs available");
    assertProblem(
        handler.handleConflict(new ConflictException("Version is stale")),
        HttpStatus.CONFLICT,
        "resource-conflict",
        "Version is stale");
    assertProblem(
        handler.handleBadRequest(new InvalidRequestException("Invalid request")),
        HttpStatus.BAD_REQUEST,
        "invalid-request",
        "Invalid request");
    assertProblem(
        handler.handleBadRequest(new IllegalArgumentException("Bad argument")),
        HttpStatus.BAD_REQUEST,
        "invalid-request",
        "Bad argument");
    assertProblem(
        handler.handleTenantAccessDenied(new TenantAccessDeniedException("No membership")),
        HttpStatus.FORBIDDEN,
        "tenant-access-denied",
        "No membership");
    assertProblem(
        handler.handleRouteProvider(
            new RouteProviderException(RouteProviderException.Reason.BAD_RESPONSE)),
        HttpStatus.BAD_GATEWAY,
        "route-provider-bad-gateway",
        "Route estimate provider failed");
    assertProblem(
        handler.handleRouteProvider(
            new RouteProviderException(RouteProviderException.Reason.UNAVAILABLE)),
        HttpStatus.SERVICE_UNAVAILABLE,
        "route-provider-unavailable",
        "Route estimate provider is unavailable");
  }

  @Test
  void mapsBeanValidationErrorsByField() {
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
    binding.addError(new FieldError("request", "name", "must not be blank"));
    binding.addError(new FieldError("request", "name", "duplicate is ignored"));
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    when(exception.getBindingResult()).thenReturn(binding);

    ResponseEntity<ProblemDetail> response = handler.handleValidation(exception);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("validation-failed", response.getBody().getProperties().get("code"));
    assertEquals(
        Map.of("name", "must not be blank"), response.getBody().getProperties().get("violations"));
  }

  @Test
  void hidesUnexpectedExceptionDetails() {
    ResponseEntity<ProblemDetail> response =
        handler.handleUnexpected(new RuntimeException("database password"));

    assertProblem(
        response, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected error");
  }

  private void assertProblem(
      ResponseEntity<ProblemDetail> response,
      HttpStatus status,
      String code,
      String expectedDetail) {
    assertEquals(status, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(expectedDetail, response.getBody().getDetail());
    assertEquals(code, response.getBody().getProperties().get("code"));
  }
}

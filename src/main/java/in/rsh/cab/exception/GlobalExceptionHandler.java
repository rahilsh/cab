package in.rsh.cab.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), "not-found");
  }

  @ExceptionHandler(CabNotAvailableException.class)
  public ResponseEntity<ProblemDetail> handleCabNotAvailable(CabNotAvailableException exception) {
    return problem(HttpStatus.CONFLICT, "Cab unavailable", exception.getMessage(), "cab-unavailable");
  }

  @ExceptionHandler({IllegalArgumentException.class, InvalidRequestException.class})
  public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), "invalid-request");
  }

  @ExceptionHandler(TenantAccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleTenantAccessDenied(
      TenantAccessDeniedException exception) {
    return problem(HttpStatus.FORBIDDEN, "Tenant access denied", exception.getMessage(), "tenant-access-denied");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> violations = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      violations.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    ProblemDetail detail =
        createProblem(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            "One or more request fields are invalid",
            "validation-failed");
    detail.setProperty("violations", violations);
    return ResponseEntity.badRequest().body(detail);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
    log.error("Unexpected error occurred", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "Unexpected error",
        "internal-error");
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String code) {
    return ResponseEntity.status(status).body(createProblem(status, title, detail, code));
  }

  private ProblemDetail createProblem(
      HttpStatus status, String title, String detail, String code) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create("https://github.com/rahilsh/cab/problems/" + code));
    problem.setProperty("code", code);
    return problem;
  }
}

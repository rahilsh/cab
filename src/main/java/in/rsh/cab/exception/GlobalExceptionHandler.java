package in.rsh.cab.exception;

import in.rsh.cab.operations.IdempotencyConflictException;
import in.rsh.cab.payment.PaymentSignatureException;
import in.rsh.cab.ratelimit.PayloadTooLargeException;
import in.rsh.cab.ratelimit.RateLimitExceededException;
import in.rsh.cab.routing.RouteProviderException;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), "not-found");
  }

  @ExceptionHandler(CabNotAvailableException.class)
  public ResponseEntity<ProblemDetail> handleCabNotAvailable(CabNotAvailableException exception) {
    return problem(
        HttpStatus.CONFLICT, "Cab unavailable", exception.getMessage(), "cab-unavailable");
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ProblemDetail> handleConflict(ConflictException exception) {
    return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage(), "resource-conflict");
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
      IdempotencyConflictException exception) {
    String code =
        exception.reason() == IdempotencyConflictException.Reason.KEY_REUSED
            ? "idempotency-key-reused"
            : "idempotency-in-progress";
    return problem(HttpStatus.CONFLICT, "Idempotency conflict", exception.getMessage(), code);
  }

  @ExceptionHandler({IllegalArgumentException.class, InvalidRequestException.class})
  public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException exception) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), "invalid-request");
  }

  @ExceptionHandler(TenantAccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleTenantAccessDenied(
      TenantAccessDeniedException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Tenant access denied",
        exception.getMessage(),
        "tenant-access-denied");
  }

  @ExceptionHandler(PaymentSignatureException.class)
  public ResponseEntity<ProblemDetail> handlePaymentSignature(PaymentSignatureException exception) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Invalid provider signature",
        exception.getMessage(),
        "invalid-provider-signature");
  }

  @ExceptionHandler(RouteProviderException.class)
  public ResponseEntity<ProblemDetail> handleRouteProvider(RouteProviderException exception) {
    if (exception.reason() == RouteProviderException.Reason.UNAVAILABLE) {
      return problem(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Route provider unavailable",
          exception.getMessage(),
          "route-provider-unavailable");
    }
    return problem(
        HttpStatus.BAD_GATEWAY,
        "Route provider failed",
        exception.getMessage(),
        "route-provider-bad-gateway");
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException exception) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
        .body(
            createProblem(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests",
                exception.getMessage(),
                "rate-limit-exceeded"));
  }

  @ExceptionHandler(PayloadTooLargeException.class)
  public ResponseEntity<ProblemDetail> handlePayloadTooLarge(PayloadTooLargeException exception) {
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "Payload too large",
        exception.getMessage(),
        "payload-too-large");
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

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<ProblemDetail> handleNotAcceptable(
      HttpMediaTypeNotAcceptableException exception) {
    return problem(
        HttpStatus.NOT_ACCEPTABLE,
        "Not acceptable",
        "The requested response media type is not supported",
        "not-acceptable");
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

  @ExceptionHandler(AsyncRequestNotUsableException.class)
  public void handleDisconnectedClient() {
    // The response is already committed; a disconnected streaming client needs no error body.
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String code) {
    return ResponseEntity.status(status).body(createProblem(status, title, detail, code));
  }

  private ProblemDetail createProblem(HttpStatus status, String title, String detail, String code) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create("https://github.com/rahilsh/cab/problems/" + code));
    problem.setProperty("code", code);
    return problem;
  }
}

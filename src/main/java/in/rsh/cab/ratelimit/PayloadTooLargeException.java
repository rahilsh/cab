package in.rsh.cab.ratelimit;

public class PayloadTooLargeException extends RuntimeException {

  public PayloadTooLargeException() {
    super("Request body exceeds the configured limit");
  }
}

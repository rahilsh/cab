package in.rsh.cab.routing;

public class RouteProviderException extends RuntimeException {

  public enum Reason {
    BAD_RESPONSE,
    UNAVAILABLE
  }

  private final Reason reason;

  public RouteProviderException(Reason reason) {
    super("Route estimate provider " + (reason == Reason.UNAVAILABLE ? "is unavailable" : "failed"));
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}

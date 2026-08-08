package in.rsh.cab.routing;

public record RouteEstimate(double distanceMeters, double durationSeconds) {

  public RouteEstimate {
    if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
      throw new IllegalArgumentException("Distance must be a non-negative finite value");
    }
    if (!Double.isFinite(durationSeconds) || durationSeconds < 0) {
      throw new IllegalArgumentException("Duration must be a non-negative finite value");
    }
  }
}

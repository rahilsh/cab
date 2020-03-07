package in.r.cab.user.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class Cab {

  private final String cabId;
  private final String driverId;
  private final String cabNumber;
  private final CabStatus status;
  private final CabType type;
  private final Location location;

  public enum CabStatus {
    AVAILABLE,
    UNAVAILABLE,
    ON_RIDE
  }

  public enum CabType {
    S,
    M,
    L
  }
}

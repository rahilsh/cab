package in.rsh.cab.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder(toBuilder = true)
public class Cab {

  private final int cabId;
  private final String driverId;
  private final String cabNumber;
  private final CabType type;
  private final Location location;
  @Setter
  private CabStatus status;
  @Setter
  private Long idleFrom;
  @Setter
  private Integer cityId;
  private String model;

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

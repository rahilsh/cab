package in.rsh.cab.commons.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder(toBuilder = true)
public class Cab {

  private final String cabId;
  private final String driverId;
  private final String cabNumber;
  @Setter private CabStatus status;
  private final CabType type;
  private final Location location;
  @Setter private Long idleFrom;
  @Setter private Integer cityId;
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

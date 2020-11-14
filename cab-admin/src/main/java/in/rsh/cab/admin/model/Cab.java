package in.rsh.cab.admin.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Cab {

  private final Integer cabId;
  private final Integer driverId;
  private final String model;
  private Integer cityId;
  private State state;
  private Long idleFrom;

  public enum State {
    IDLE,
    ON_TRIP,
    UNAVAILABLE
  }
}

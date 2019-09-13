package in.rahilsh.cab.admin.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Cab {

  Integer cabId;
  Integer driverId;
  Integer cityId;
  String model;
  State state;
  Long idleFrom;

  public enum State {
    IDLE,
    ON_TRIP,
    UNAVAILABLE
  }
}

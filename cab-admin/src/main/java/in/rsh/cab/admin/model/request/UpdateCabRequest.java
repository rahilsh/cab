package in.rsh.cab.admin.model.request;

import in.rsh.cab.admin.model.Cab;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UpdateCabRequest {

  private final String state;
  private final Integer cityId;

  public void validate() {
    if ((state == null && cityId == null)) {
      throw new IllegalArgumentException("Invalid Params");
    }
    if (state != null && Cab.State.valueOf(state).equals(Cab.State.ON_TRIP)) {
      throw new IllegalArgumentException("Invalid State Transition");
    }
  }
}

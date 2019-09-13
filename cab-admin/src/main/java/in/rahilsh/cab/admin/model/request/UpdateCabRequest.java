package in.rahilsh.cab.admin.model.request;

import in.rahilsh.cab.admin.model.Cab.State;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UpdateCabRequest {

  String state;
  Integer cityId;

  public void validate() {
    if ((state == null && cityId == null)) {
      throw new IllegalArgumentException("Invalid Params");
    }
    if (state != null && State.valueOf(state).equals(State.ON_TRIP)) {
      throw new IllegalArgumentException("Invalid State Transition");
    }
  }
}

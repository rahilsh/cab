package in.rsh.cab.admin.model.request;

import static in.rsh.cab.commons.model.Cab.*;

public record UpdateCabRequest(String state, Integer cityId) {

  public void validate() {
    if ((state == null && cityId == null)) {
      throw new IllegalArgumentException("Invalid Params");
    }
    if (state != null && CabStatus.valueOf(state).equals(CabStatus.ON_RIDE)) {
      throw new IllegalArgumentException("Invalid State Transition");
    }
  }
}

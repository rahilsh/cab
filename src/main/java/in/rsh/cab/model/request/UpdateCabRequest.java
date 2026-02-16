package in.rsh.cab.model.request;

import static in.rsh.cab.model.Cab.CabStatus;

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

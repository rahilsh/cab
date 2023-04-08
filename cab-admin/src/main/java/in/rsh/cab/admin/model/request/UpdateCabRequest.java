package in.rsh.cab.admin.model.request;

import in.rsh.cab.admin.model.Cab;

public record UpdateCabRequest(String state, Integer cityId) {

  public void validate() {
    if ((state == null && cityId == null)) {
      throw new IllegalArgumentException("Invalid Params");
    }
    if (state != null && Cab.State.valueOf(state).equals(Cab.State.ON_TRIP)) {
      throw new IllegalArgumentException("Invalid State Transition");
    }
  }
}

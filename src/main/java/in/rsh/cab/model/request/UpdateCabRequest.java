package in.rsh.cab.model.request;

import static in.rsh.cab.model.Cab.CabStatus;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record UpdateCabRequest(
    @Pattern(regexp = "AVAILABLE|UNAVAILABLE") String state, @Positive Integer cityId) {

  public void validate() {
    if ((state == null && cityId == null)) {
      throw new IllegalArgumentException("Invalid Params");
    }
    if (state != null && CabStatus.valueOf(state).equals(CabStatus.ON_RIDE)) {
      throw new IllegalArgumentException("Invalid State Transition");
    }
  }
}

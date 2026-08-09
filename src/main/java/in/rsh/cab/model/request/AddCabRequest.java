package in.rsh.cab.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddCabRequest(
    @NotNull @Positive Integer cityId,
    @NotNull @Positive Integer driverId,
    @NotNull @Size(min = 1, max = 100) String model) {

  public void validate() {
    if (cityId == null || driverId == null || model == null) {
      throw new IllegalArgumentException("Missing param");
    }
  }
}

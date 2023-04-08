package in.rsh.cab.admin.model.request;

import jakarta.validation.constraints.NotNull;

public record AddCabRequest(@NotNull Integer cityId, @NotNull Integer driverId,
                            @NotNull String model) {

  public void validate() {
    if (cityId == null || driverId == null || model == null) {
      throw new IllegalArgumentException("Missing param");
    }
  }
}

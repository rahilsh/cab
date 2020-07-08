package in.rsh.cab.admin.model.request;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddCabRequest {

  @NotNull private final Integer cityId;
  @NotNull private final Integer driverId;
  @NotNull private final String model;

  public void validate() {
    if (cityId == null || driverId == null || model == null) {
      throw new IllegalArgumentException("Missing param");
    }
  }
}

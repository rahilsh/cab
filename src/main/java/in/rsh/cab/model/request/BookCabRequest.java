package in.rsh.cab.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookCabRequest(
    @NotBlank @Size(max = 100) String employeeId,
    @NotNull @Positive Integer fromCity,
    @NotNull @Positive Integer toCity) {

  public void validate() {
    if (employeeId == null || fromCity == null || toCity == null) {
      throw new IllegalArgumentException("Missing mandatory params");
    }
  }
}

package in.rsh.cab.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCityRequest(
    @NotBlank @Size(max = 100) String name, @Size(max = 100) String state) {

  public void validate() {
    if (name == null) {
      throw new IllegalArgumentException("Missing param");
    }
  }
}

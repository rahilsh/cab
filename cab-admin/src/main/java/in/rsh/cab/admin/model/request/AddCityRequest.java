package in.rsh.cab.admin.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddCityRequest {

  private final String name;
  private final String state;

  public void validate() {
    if (name == null) {
      throw new IllegalArgumentException("Missing param");
    }
  }
}

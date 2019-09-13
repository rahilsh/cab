package in.rahilsh.cab.admin.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddCityRequest {

  String name;
  String state;

  public void validate() {
    if (name == null) {
      throw new IllegalArgumentException("Missing param");
    }
  }
}

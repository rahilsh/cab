package in.r.cab.admin.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class BookCabRequest {

  Integer employeeId;
  Integer fromCity;
  Integer toCity;

  public void validate() {
    if (employeeId == null || fromCity == null || toCity == null) {
      throw new IllegalArgumentException("Missing mandatory params");
    }
  }
}

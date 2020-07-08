package in.rsh.cab.admin.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class BookCabRequest {

  private final Integer employeeId;
  private final Integer fromCity;
  private final Integer toCity;

  public void validate() {
    if (employeeId == null || fromCity == null || toCity == null) {
      throw new IllegalArgumentException("Missing mandatory params");
    }
  }
}

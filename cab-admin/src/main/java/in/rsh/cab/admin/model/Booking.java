package in.rsh.cab.admin.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Booking {

  private final Integer id;
  private final Integer cabId;
  private final Integer employeeId;
  private final Integer fromCity;
  private final Integer toCity;
  private final Long startTime;
}

package in.r.cab.admin.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Booking {

  Integer id;
  Integer cabId;
  Integer employeeId;
  Integer fromCity;
  Integer toCity;
  Long startTime;

}

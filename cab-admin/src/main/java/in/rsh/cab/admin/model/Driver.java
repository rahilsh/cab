package in.rsh.cab.admin.model;

import lombok.Getter;

@Getter
public class Driver extends Person {

  private final String licenseNo;

  public Driver(Integer id, String name, String licenseNo) {
    super(id, name);
    this.licenseNo = licenseNo;
  }
}

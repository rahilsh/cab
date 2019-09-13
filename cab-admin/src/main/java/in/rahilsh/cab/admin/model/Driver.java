package in.rahilsh.cab.admin.model;

import lombok.Getter;

@Getter
public class Driver extends Person {

  String licenseNo;

  public Driver(Integer id, String name) {
    super(id, name);
  }

  public Driver(Integer id, String name, String licenseNo) {
    super(id, name);
    this.licenseNo = licenseNo;
  }
}

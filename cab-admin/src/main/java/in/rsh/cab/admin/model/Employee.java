package in.rsh.cab.admin.model;

import lombok.Getter;

@Getter
public class Employee extends Person {

  private final String empId;

  public Employee(Integer id, String name, String empId) {
    super(id, name);
    this.empId = empId;
  }
}

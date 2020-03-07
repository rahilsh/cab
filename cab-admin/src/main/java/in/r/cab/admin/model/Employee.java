package in.r.cab.admin.model;

import lombok.Getter;

@Getter
public class Employee extends Person {

  public Employee(Integer id, String name) {
    super(id, name);
  }
}

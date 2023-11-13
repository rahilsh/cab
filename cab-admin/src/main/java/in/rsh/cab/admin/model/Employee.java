package in.rsh.cab.admin.model;

import in.rsh.cab.commons.model.Person;
import lombok.Getter;

@Getter
public class Employee extends Person {

  public Employee(String empId, String name, String email, String phoneNumber, String photoURL) {
    super(empId, name, email, phoneNumber, photoURL);
  }
}

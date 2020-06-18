package in.rsh.cab.user.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Person {

  protected final String personId;
  protected final String name;
  protected final String email;
  protected final String phoneNumber;
  protected final String photoURL;
}

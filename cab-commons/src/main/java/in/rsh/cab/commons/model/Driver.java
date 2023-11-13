package in.rsh.cab.commons.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class Driver extends Person {

  private final DrivingLicense drivingLicense;

  public Driver(DriverBuilder builder) {
    super(
        builder.getPersonId(),
        builder.getName(),
        builder.getEmail(),
        builder.getPhoneNumber(),
        builder.getPhotoURL());
    this.drivingLicense = builder.drivingLicense;
  }

  public static class DriverBuilder {

    @Getter private String personId;
    @Getter private String name;
    @Getter private String email;
    @Getter private String phoneNumber;
    @Getter private String photoURL;

    public DriverBuilder personId(String personId) {
      this.personId = personId;
      return this;
    }

    public DriverBuilder name(String name) {
      this.name = name;
      return this;
    }

    public DriverBuilder email(String email) {
      this.email = email;
      return this;
    }

    public DriverBuilder phoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
      return this;
    }

    public DriverBuilder photoURL(String photoURL) {
      this.photoURL = photoURL;
      return this;
    }

    public Driver build() {
      return new Driver(this);
    }
  }
}

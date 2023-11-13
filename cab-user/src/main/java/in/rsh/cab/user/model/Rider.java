package in.rsh.cab.user.model;

import in.rsh.cab.commons.model.Location;
import in.rsh.cab.commons.model.Person;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
public class Rider extends Person {

  private Location currentLocation;
  private Location home;
  private Location work;

  public Rider(RiderBuilder builder) {
    super(
        builder.getPersonId(),
        builder.getName(),
        builder.getEmail(),
        builder.getPhoneNumber(),
        builder.getPhotoURL());
    this.currentLocation = builder.currentLocation;
  }

  public static class RiderBuilder {

    private String personId;
    private String name;
    private String email;
    private String phoneNumber;
    private String photoURL;

    public String getPersonId() {
      return personId;
    }

    public String getName() {
      return name;
    }

    public String getEmail() {
      return email;
    }

    public String getPhoneNumber() {
      return phoneNumber;
    }

    public String getPhotoURL() {
      return photoURL;
    }

    public RiderBuilder personId(String personId) {
      this.personId = personId;
      return this;
    }

    public RiderBuilder name(String name) {
      this.name = name;
      return this;
    }

    public RiderBuilder email(String email) {
      this.email = email;
      return this;
    }

    public RiderBuilder phoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
      return this;
    }

    public RiderBuilder photoURL(String photoURL) {
      this.photoURL = photoURL;
      return this;
    }

    public Rider build() {
      return new Rider(this);
    }
  }
}

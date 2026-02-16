package in.rsh.cab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CityTest {

  @Test
  void cityRecord_shouldCreateCity() {
    City city = new City(1, "New York");
    assertEquals(1, city.id());
    assertEquals("New York", city.name());
  }
}

class LocationTest {

  @Test
  void location_shouldCreateLocation() {
    Location location = new Location(10, 20);
    assertEquals(10, location.latitude());
    assertEquals(20, location.longitude());
  }
}

class PersonTest {

  @Test
  void person_shouldCreatePerson() {
    Person person = new Person("1", "John", "john@test.com", "1234567890", "http://photo.url");
    assertEquals("1", person.getPersonId());
    assertEquals("John", person.getName());
    assertEquals("john@test.com", person.getEmail());
    assertEquals("1234567890", person.getPhoneNumber());
    assertEquals("http://photo.url", person.getPhotoURL());
  }
}

class DriverTest {

  @Test
  void driverBuilder_shouldBuildDriver() {
    Driver driver = Driver.builder()
        .personId("1")
        .name("John")
        .email("john@test.com")
        .phoneNumber("1234567890")
        .photoURL("http://photo.url")
        .drivingLicense(new DrivingLicense("DL123", "有效性"))
        .build();

    assertNotNull(driver);
    assertEquals("1", driver.getPersonId());
    assertEquals("John", driver.getName());
    assertNotNull(driver.getDrivingLicense());
  }

  @Test
  void driver_shouldExtendPerson() {
    Driver driver = Driver.builder()
        .personId("1")
        .name("John")
        .build();
    assertNotNull(driver);
  }
}

class DrivingLicenseTest {

  @Test
  void drivingLicense_shouldCreateLicense() {
    DrivingLicense license = new DrivingLicense("http://photo.url", "DL123");
    assertEquals("http://photo.url", license.photoURL());
    assertEquals("DL123", license.licenseNo());
  }
}

class RiderTest {

  @Test
  void riderBuilder_shouldBuildRider() {
    Rider rider = Rider.builder()
        .personId("1")
        .name("Alice")
        .email("alice@test.com")
        .phoneNumber("9876543210")
        .photoURL("http://alice.photo")
        .currentLocation(new Location(10, 20))
        .build();

    assertNotNull(rider);
    assertEquals("1", rider.getPersonId());
    assertEquals("Alice", rider.getName());
    assertNotNull(rider.getCurrentLocation());
  }
}

class CabModelTest {

  @Test
  void cabBuilder_shouldBuildCab() {
    Cab cab = Cab.builder()
        .cabId(1)
        .driverId("D1")
        .cabNumber("ABC123")
        .type(Cab.CabType.M)
        .location(new Location(10, 20))
        .cityId(1)
        .model("Toyota Innova")
        .build();

    assertNotNull(cab);
    assertEquals(1, cab.getCabId());
    assertEquals("D1", cab.getDriverId());
    assertEquals("ABC123", cab.getCabNumber());
    assertEquals(Cab.CabType.M, cab.getType());
    assertEquals(1, cab.getCityId());
  }

  @Test
  void cabStatus_shouldHaveAvailable() {
    assertNotNull(Cab.CabStatus.AVAILABLE);
    assertNotNull(Cab.CabStatus.UNAVAILABLE);
    assertNotNull(Cab.CabStatus.ON_RIDE);
  }

  @Test
  void cabType_shouldHaveTypes() {
    assertNotNull(Cab.CabType.S);
    assertNotNull(Cab.CabType.M);
    assertNotNull(Cab.CabType.L);
  }
}

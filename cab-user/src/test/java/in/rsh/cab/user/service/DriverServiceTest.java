package in.rsh.cab.user.service;

import in.rsh.cab.commons.model.Driver;
import in.rsh.cab.commons.model.DrivingLicense;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverServiceTest {

  private final DriverService driverService = new DriverService();

  @Test
  void registerDriver() {

    Driver driver =
        driverService.registerDriver(
            Driver.builder()
                .name("test")
                .email("test@test.com")
                .phoneNumber("+919999999999")
                .photoURL("test")
                .drivingLicense(new DrivingLicense("test.com/photo", "testNo"))
                .build());
    assertEquals(driver, driverService.getDriver(driver.getPersonId()));
  }

  @Test
  void updateDriver() {}

  @Test
  void getDriver() {}
}

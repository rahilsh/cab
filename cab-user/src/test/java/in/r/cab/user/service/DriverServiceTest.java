package in.r.cab.user.service;

import static org.junit.Assert.assertEquals;

import in.r.cab.user.model.Driver;
import in.r.cab.user.model.DrivingLicense;
import org.junit.Test;

public class DriverServiceTest {

  private final DriverService driverService = new DriverService();

  @Test
  public void registerDriver() {

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
  public void updateDriver() {

  }

  @Test
  public void getDriver() {}
}

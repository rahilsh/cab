package in.r.cab.user.service;

import in.r.cab.user.model.Booking;
import in.r.cab.user.model.Cab;
import in.r.cab.user.model.Driver;
import in.r.cab.user.model.DrivingLicense;
import in.r.cab.user.model.Location;
import in.r.cab.user.model.Rider;
import org.junit.Assert;
import org.junit.Test;

public class BookingServiceTest {

  private final BookingService bookingService = new BookingService();
  private final RiderService riderService = new RiderService();
  private final DriverService driverService = new DriverService();
  private final CabService cabService = new CabService();

  @Test
  public void bookCab() {
    Rider rider =
        riderService.registerRider(
            Rider.builder()
                .name("test")
                .email("test@test.com")
                .phoneNumber("+919999999999")
                .photoURL("test")
                .currentLocation(new Location(2,2))
                .build());
    Driver driver =
        driverService.registerDriver(
            Driver.builder()
                .name("test")
                .email("test@test.com")
                .phoneNumber("+919999999999")
                .photoURL("test")
                .drivingLicense(new DrivingLicense("test.com/photo", "testNo"))
                .build());
    Cab cab = cabService.registerCab(Cab.builder().driverId(driver.getPersonId()).location(new Location(1, 1)).build());

    Booking booking = bookingService.bookCab(rider);
    Assert.assertEquals(cab.getCabId(), booking.getCabId());
  }

  @Test
  public void getBookingsForRider() {}
}

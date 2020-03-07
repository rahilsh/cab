package in.r.cab.user.service;

import static org.junit.Assert.assertEquals;

import in.r.cab.user.model.Rider;
import org.junit.Test;

public class RiderServiceTest {

  private final RiderService riderService = new RiderService();

  @Test
  public void registerRider() {
    Rider rider =
        riderService.registerRider(
            Rider.builder()
                .name("test")
                .email("test@test.com")
                .phoneNumber("+919999999999")
                .photoURL("test")
                .build());
    assertEquals(rider, riderService.getRider(rider.getPersonId()));
  }
}

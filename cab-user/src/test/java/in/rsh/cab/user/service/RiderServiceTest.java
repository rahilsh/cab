package in.rsh.cab.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.rsh.cab.user.model.Rider;
import org.junit.jupiter.api.Test;

class RiderServiceTest {

  private final RiderService riderService = new RiderService();

  @Test
  void registerRider() {
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

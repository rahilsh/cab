package in.rsh.cab.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RefundAllocationTest {

  @Test
  void partialRefundsExactlyConvergeToCaptureSplit() {
    long driver = 0;
    long platform = 0;
    for (int refunded = 0; refunded < 809; refunded++) {
      RefundAllocation allocation = RefundAllocation.cumulative(809, 688, refunded, driver, 1);
      driver += allocation.driverShareMinor();
      platform += allocation.platformShareMinor();
    }

    assertEquals(688, driver);
    assertEquals(121, platform);
  }

  @Test
  void supportsZeroAndFullCommissionSplits() {
    assertEquals(new RefundAllocation(10, 0), RefundAllocation.cumulative(10, 10, 0, 0, 10));
    assertEquals(new RefundAllocation(0, 10), RefundAllocation.cumulative(10, 0, 0, 0, 10));
  }
}

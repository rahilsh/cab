package in.rsh.cab.routing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RouteEstimateTest {

  @Test
  void validatesProviderMeasurements() {
    assertDoesNotThrow(() -> new RouteEstimate(0, 0));
    assertThrows(IllegalArgumentException.class, () -> new RouteEstimate(-1, 1));
    assertThrows(IllegalArgumentException.class, () -> new RouteEstimate(Double.NaN, 1));
    assertThrows(IllegalArgumentException.class, () -> new RouteEstimate(1, -1));
    assertThrows(IllegalArgumentException.class, () -> new RouteEstimate(1, Double.POSITIVE_INFINITY));
  }
}

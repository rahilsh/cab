package in.rsh.cab.geography;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GeoPointTest {

  @Test
  void acceptsInclusiveCoordinateBounds() {
    assertDoesNotThrow(() -> new GeoPoint(-90, -180));
    GeoPoint point = new GeoPoint(90, 180);
    assertEquals(90, point.latitude());
    assertEquals(180, point.longitude());
  }

  @Test
  void rejectsLatitudeOutsideBoundsOrNonFinite() {
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(-90.01, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(90.01, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.POSITIVE_INFINITY, 0));
  }

  @Test
  void rejectsLongitudeOutsideBoundsOrNonFinite() {
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, -180.01));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, 180.01));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, Double.NEGATIVE_INFINITY));
  }
}

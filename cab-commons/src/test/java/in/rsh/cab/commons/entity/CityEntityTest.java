package in.rsh.cab.commons.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityEntityTest {

  @Test
  void testCityEntityCreation() {
    CityEntity entity = new CityEntity(1, "Bangalore");
    assertEquals(1, entity.getId());
    assertEquals("Bangalore", entity.getName());
  }

  @Test
  void testCityEntityNoArgsConstructor() {
    CityEntity entity = new CityEntity();
    assertNull(entity.getId());
    assertNull(entity.getName());
  }

  @Test
  void testCityEntityNameConstructor() {
    CityEntity entity = new CityEntity("Mumbai");
    assertNull(entity.getId());
    assertEquals("Mumbai", entity.getName());
  }

  @Test
  void testCityEntityAllArgsConstructor() {
    CityEntity entity = new CityEntity(2, "Delhi");
    assertEquals(2, entity.getId());
    assertEquals("Delhi", entity.getName());
  }
}

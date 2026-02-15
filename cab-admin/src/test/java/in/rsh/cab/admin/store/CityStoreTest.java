package in.rsh.cab.admin.store;

import in.rsh.cab.admin.model.City;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityStoreTest {

  private CityStore cityStore;

  @BeforeEach
  void setUp() {
    cityStore = new CityStore();
  }

  @Nested
  class AddCityTests {

    @Test
    void add_shouldCreateCity() {
      cityStore.add("Bangalore");
      
      var cities = cityStore.cities();
      assertEquals(1, cities.size());
      
      City city = cities.iterator().next();
      assertEquals("Bangalore", city.name());
    }

    @Test
    void add_multipleCities_shouldAssignUniqueIds() {
      cityStore.add("Bangalore");
      cityStore.add("Mumbai");
      cityStore.add("Delhi");
      
      var cities = cityStore.cities();
      assertEquals(3, cities.size());
    }
  }

  @Nested
  class GetCitiesTests {

    @Test
    void cities_shouldReturnAllCities() {
      cityStore.add("Bangalore");
      cityStore.add("Mumbai");
      
      var cities = cityStore.cities();
      assertEquals(2, cities.size());
    }

    @Test
    void cities_emptyStore_shouldReturnEmptyCollection() {
      var cities = cityStore.cities();
      assertTrue(cities.isEmpty());
    }
  }

  @Nested
  class GetCityTests {

    @Test
    void getCity_withValidId_shouldReturnCity() {
      cityStore.add("Bangalore");
      
      City city = cityStore.getCity(1);
      
      assertNotNull(city);
      assertEquals("Bangalore", city.name());
    }

    @Test
    void getCity_withInvalidId_shouldReturnNull() {
      City city = cityStore.getCity(999);
      
      assertNull(city);
    }
  }
}

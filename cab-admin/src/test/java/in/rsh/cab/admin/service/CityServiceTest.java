package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.NotFoundException;
import in.rsh.cab.admin.model.City;
import in.rsh.cab.admin.store.CityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CityServiceTest {

  @Mock
  private CityStore cityStore;

  @InjectMocks
  private CityService cityService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  class AddCityTests {

    @Test
    void addCity_shouldCallStoreAdd() {
      cityService.addCity("Bangalore");
      
      verify(cityStore).add("Bangalore");
    }
  }

  @Nested
  class GetAllCitiesTests {

    @Test
    void getAllCities_shouldReturnAllCities() {
      when(cityStore.cities()).thenReturn(List.of());
      
      var cities = cityService.getAllCities();
      
      assertNotNull(cities);
      verify(cityStore).cities();
    }
  }

  @Nested
  class ValidateCityOrThrowTests {

    @Test
    void validateCityOrThrow_withValidCity_shouldNotThrow() {
      when(cityStore.getCity(1)).thenReturn(new City(1, "Bangalore"));
      
      assertDoesNotThrow(() -> cityService.validateCityOrThrow(1));
    }

    @Test
    void validateCityOrThrow_withInvalidCity_shouldThrowNotFoundException() {
      when(cityStore.getCity(999)).thenReturn(null);
      
      NotFoundException exception = assertThrows(
          NotFoundException.class,
          () -> cityService.validateCityOrThrow(999));
      
      assertEquals("City does not exist", exception.getMessage());
    }
  }
}

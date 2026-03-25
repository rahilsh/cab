package in.rsh.cab.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.entity.CityEntity;
import in.rsh.cab.model.response.CityResponse;
import in.rsh.cab.repository.CityJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CityServiceTest {

  @Mock
  private CityJpaRepository cityJpaRepository;

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

      verify(cityJpaRepository).save(any(CityEntity.class));
    }
  }

  @Nested
  class GetAllCitiesTests {

    @Test
    void getAllCities_shouldReturnAllCities() {
      when(cityJpaRepository.findAll()).thenReturn(List.of());

      var cities = cityService.getAllCities();

      assertNotNull(cities);
      verify(cityJpaRepository).findAll();
    }

    @Test
    void getAllCities_shouldReturnMultipleCities() {
      CityEntity city1 = new CityEntity("Bangalore");
      CityEntity city2 = new CityEntity("Delhi");
      when(cityJpaRepository.findAll()).thenReturn(List.of(city1, city2));

      var cities = cityService.getAllCities();

      assertEquals(2, cities.size());
    }
  }

  @Nested
  class GetAllCitiesResponseTests {

    @Test
    void getAllCitiesResponse_shouldReturnEmptyListWhenNoCities() {
      when(cityJpaRepository.findAll()).thenReturn(List.of());

      var cities = cityService.getAllCitiesResponse();

      assertNotNull(cities);
      assertEquals(0, cities.size());
    }

    @Test
    void getAllCitiesResponse_shouldReturnCityResponses() {
      CityEntity city1 = new CityEntity("Bangalore");
      CityEntity city2 = new CityEntity("Delhi");
      when(cityJpaRepository.findAll()).thenReturn(List.of(city1, city2));

      List<CityResponse> cities = cityService.getAllCitiesResponse();

      assertEquals(2, cities.size());
      assertEquals("Bangalore", cities.get(0).name());
      assertEquals("Delhi", cities.get(1).name());
    }
  }

  @Nested
  class ValidateCityOrThrowTests {

    @Test
    void validateCityOrThrow_withValidCity_shouldNotThrow() {
      when(cityJpaRepository.findById(1)).thenReturn(
          java.util.Optional.of(new CityEntity("Bangalore")));

      assertDoesNotThrow(() -> cityService.validateCityOrThrow(1));
    }

    @Test
    void validateCityOrThrow_withInvalidCity_shouldThrowNotFoundException() {
      when(cityJpaRepository.findById(999)).thenReturn(java.util.Optional.empty());

      NotFoundException exception = assertThrows(
          NotFoundException.class,
          () -> cityService.validateCityOrThrow(999));

      assertEquals("City does not exist", exception.getMessage());
    }
  }
}

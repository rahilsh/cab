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

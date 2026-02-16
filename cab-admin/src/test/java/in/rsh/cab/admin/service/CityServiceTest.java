package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.NotFoundException;
import in.rsh.cab.commons.entity.CityEntity;
import in.rsh.cab.commons.repository.CityJpaRepository;
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
      when(cityJpaRepository.findById(1)).thenReturn(java.util.Optional.of(new CityEntity("Bangalore")));
      
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

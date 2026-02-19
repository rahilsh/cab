package in.rsh.cab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.entity.CabEntity;
import in.rsh.cab.model.Cab;
import in.rsh.cab.repository.CabJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CabServiceTest {

  @Mock
  private CabJpaRepository cabJpaRepository;

  @Mock
  private CityService cityService;

  @Mock
  private RedisGeoService redisGeoService;

  @InjectMocks
  private CabService cabService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  class AddCabTests {

    @Test
    void addCab_shouldAddCabToStore() {
      doNothing().when(cityService).validateCityOrThrow(1);

      cabService.addCab(1, 1, "Toyota Innova");

      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void addCab_withInvalidCity_shouldThrowException() {
      doThrow(new in.rsh.cab.exception.NotFoundException("City does not exist"))
          .when(cityService).validateCityOrThrow(999);

      assertThrows(in.rsh.cab.exception.NotFoundException.class,
          () -> cabService.addCab(1, 999, "Toyota Innova"));
    }
  }

  @Nested
  class GetAllCabsTests {

    @Test
    void getAllCabs_shouldReturnAllCabs() {
      when(cabJpaRepository.findAll()).thenReturn(List.of());

      var cabs = cabService.getAllCabs();

      assertNotNull(cabs);
      verify(cabJpaRepository).findAll();
    }
  }

  @Nested
  class UpdateCabTests {

    @Test
    void updateCab_shouldUpdateCabStatus() {
      doNothing().when(cityService).validateCityOrThrow(1);
      CabEntity cabEntity = new CabEntity();
      cabEntity.setStatus(CabEntity.CabStatus.AVAILABLE);
      cabEntity.setId(1);
      when(cabJpaRepository.findById(anyInt())).thenReturn(Optional.of(cabEntity));

      cabService.updateCab(1, 1, Cab.CabStatus.UNAVAILABLE);

      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void updateCab_withInvalidCity_shouldThrowException() {
      doThrow(new in.rsh.cab.exception.NotFoundException("City does not exist"))
          .when(cityService).validateCityOrThrow(999);

      assertThrows(in.rsh.cab.exception.NotFoundException.class,
          () -> cabService.updateCab(1, 999, Cab.CabStatus.AVAILABLE));
    }
  }

  @Nested
  class GetIdleCabsInCityTests {

    @Test
    void getIdleCabsInCity_shouldReturnIdleCabs() {
      CabEntity cab = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabJpaRepository.findByCityId(1)).thenReturn(List.of(cab));

      var idleCabs = cabService.getIdleCabsInCity(1);

      assertEquals(1, idleCabs.size());
    }

    @Test
    void getIdleCabsInCity_withNoIdleCabs_shouldReturnEmptyList() {
      CabEntity cab = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.UNAVAILABLE)
          .cityId(1)
          .build();
      when(cabJpaRepository.findByCityId(1)).thenReturn(List.of(cab));

      var idleCabs = cabService.getIdleCabsInCity(1);

      assertTrue(idleCabs.isEmpty());
    }
  }

  @Nested
  class ReserveMostSuitableCabTests {

    @Test
    void reserveMostSuitableCab_shouldCallStore() {
      CabEntity cab = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabJpaRepository.findByCityId(1)).thenReturn(List.of(cab));

      var result = cabService.reserveMostSuitableCab(1, 2);

      assertNotNull(result);
      verify(cabJpaRepository).save(any(CabEntity.class));
    }
  }

  @Nested
  class UpdateWithIdleFromTests {

    @Test
    void update_withIdleFrom_shouldUpdateCab() {
      CabEntity cabEntity = new CabEntity();
      cabEntity.setStatus(CabEntity.CabStatus.AVAILABLE);
      cabEntity.setId(1);
      when(cabJpaRepository.findById(anyInt())).thenReturn(Optional.of(cabEntity));
      cabService.update(1, 1000, Cab.CabStatus.AVAILABLE, System.currentTimeMillis());

      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void update_withNullCabId_shouldThrowNotFoundException() {
      when(cabJpaRepository.findById(anyInt())).thenReturn(Optional.empty());

      assertThrows(in.rsh.cab.exception.NotFoundException.class,
          () -> cabService.update(1, 1, Cab.CabStatus.AVAILABLE, null));
    }

    @Test
    void update_withOnRideStatus_shouldStartRide() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));

      cabService.update(1, null, Cab.CabStatus.ON_RIDE, null);

      verify(cabJpaRepository).save(any(CabEntity.class));
    }
  }

  @Nested
  class UpdateCabLocationTests {

    @Test
    void updateCabLocation_shouldUpdateLocation() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));

      boolean result = cabService.updateCabLocation(1, new in.rsh.cab.model.Location(10, 20));

      assertTrue(result);
      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void updateCabLocation_withInvalidCabId_shouldReturnFalse() {
      when(cabJpaRepository.findById(1)).thenReturn(Optional.empty());

      boolean result = cabService.updateCabLocation(1, new in.rsh.cab.model.Location(10, 20));

      assertEquals(false, result);
    }
  }

  @Nested
  class GetCabByDriverTests {

    @Test
    void getCabByDriver_shouldReturnCab() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .driverId("D1")
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findAll()).thenReturn(List.of(cabEntity));

      var result = cabService.getCabByDriver("D1");

      assertTrue(result.isPresent());
      assertEquals(1, result.get().getCabId());
    }

    @Test
    void getCabByDriver_withNoDriver_shouldReturnEmpty() {
      when(cabJpaRepository.findAll()).thenReturn(List.of());

      var result = cabService.getCabByDriver("D1");

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class UpdateCabStatusTests {

    @Test
    void updateCabStatus_shouldUpdateStatus() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));

      boolean result = cabService.updateCabStatus(1, Cab.CabStatus.UNAVAILABLE);

      assertTrue(result);
      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void updateCabStatus_withInvalidCabId_shouldReturnFalse() {
      when(cabJpaRepository.findById(1)).thenReturn(Optional.empty());

      boolean result = cabService.updateCabStatus(1, Cab.CabStatus.UNAVAILABLE);

      assertEquals(false, result);
    }
  }
}

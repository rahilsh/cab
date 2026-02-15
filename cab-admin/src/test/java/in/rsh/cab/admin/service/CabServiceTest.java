package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.CabNotAvailableException;
import in.rsh.cab.admin.store.CabStore;
import in.rsh.cab.commons.model.Cab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CabServiceTest {

  @Mock
  private CabStore cabStore;

  @Mock
  private CityService cityService;

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
      
      verify(cabStore).add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
    }

    @Test
    void addCab_withInvalidCity_shouldThrowException() {
      doThrow(new in.rsh.cab.admin.exception.NotFoundException("City does not exist"))
          .when(cityService).validateCityOrThrow(999);
      
      assertThrows(in.rsh.cab.admin.exception.NotFoundException.class,
          () -> cabService.addCab(1, 999, "Toyota Innova"));
    }
  }

  @Nested
  class GetAllCabsTests {

    @Test
    void getAllCabs_shouldReturnAllCabs() {
      when(cabStore.cabs()).thenReturn(List.of());
      
      var cabs = cabService.getAllCabs();
      
      assertNotNull(cabs);
      verify(cabStore).cabs();
    }
  }

  @Nested
  class UpdateCabTests {

    @Test
    void updateCab_shouldUpdateCabStatus() {
      doNothing().when(cityService).validateCityOrThrow(1);
      
      cabService.updateCab(1, 1, Cab.CabStatus.UNAVAILABLE);
      
      verify(cabStore).update(1, 1, Cab.CabStatus.UNAVAILABLE, null);
    }

    @Test
    void updateCab_withInvalidCity_shouldThrowException() {
      doThrow(new in.rsh.cab.admin.exception.NotFoundException("City does not exist"))
          .when(cityService).validateCityOrThrow(999);
      
      assertThrows(in.rsh.cab.admin.exception.NotFoundException.class,
          () -> cabService.updateCab(1, 999, Cab.CabStatus.AVAILABLE));
    }
  }

  @Nested
  class GetIdleCabsInCityTests {

    @Test
    void getIdleCabsInCity_shouldReturnIdleCabs() {
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabStore.cabs()).thenReturn(List.of(cab));
      
      var idleCabs = cabService.getIdleCabsInCity(1);
      
      assertEquals(1, idleCabs.size());
    }

    @Test
    void getIdleCabsInCity_withNoIdleCabs_shouldReturnEmptyList() {
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.UNAVAILABLE)
          .cityId(1)
          .build();
      when(cabStore.cabs()).thenReturn(List.of(cab));
      
      var idleCabs = cabService.getIdleCabsInCity(1);
      
      assertTrue(idleCabs.isEmpty());
    }
  }

  @Nested
  class GetMostSuitableCabTests {

    @Test
    void getMostSuitableCab_withAvailableCabs_shouldReturnCab() {
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .idleFrom(System.currentTimeMillis())
          .build();
      when(cabStore.cabs()).thenReturn(List.of(cab));
      
      var result = cabService.getMostSuitableCab(1);
      
      assertNotNull(result);
    }

    @Test
    void getMostSuitableCab_withNoAvailableCabs_shouldThrowException() {
      when(cabStore.cabs()).thenReturn(List.of());
      
      assertThrows(CabNotAvailableException.class,
          () -> cabService.getMostSuitableCab(1));
    }
  }

  @Nested
  class ReserveMostSuitableCabTests {

    @Test
    void reserveMostSuitableCab_shouldCallStore() {
      Cab cab = Cab.builder()
          .cabId("1")
          .status(Cab.CabStatus.AVAILABLE)
          .cityId(1)
          .build();
      when(cabStore.reserveMostSuitableCab(1, 2)).thenReturn(cab);
      
      var result = cabService.reserveMostSuitableCab(1, 2);
      
      assertNotNull(result);
      verify(cabStore).reserveMostSuitableCab(1, 2);
    }
  }

  @Nested
  class UpdateWithIdleFromTests {

    @Test
    void update_withIdleFrom_shouldUpdateCab() {
      cabService.update(1, Cab.CabStatus.AVAILABLE, 1000L);
      
      verify(cabStore).update(1, null, Cab.CabStatus.AVAILABLE, 1000L);
    }
  }
}

package in.rsh.cab.admin.store;

import in.rsh.cab.admin.exception.CabNotAvailableException;
import in.rsh.cab.admin.exception.NotFoundException;
import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Location;
import in.rsh.cab.commons.strategy.CabSelectionStrategy;
import in.rsh.cab.commons.strategy.DistanceBasedSelectionStrategy;
import in.rsh.cab.commons.strategy.IdleTimeSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CabStoreTest {

  private CabStore cabStore;

  @BeforeEach
  void setUp() {
    cabStore = new CabStore();
  }

  @Nested
  class AddCabTests {

    @Test
    void add_shouldCreateCabWithAvailableStatus() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      
      var cabs = cabStore.cabs();
      assertEquals(1, cabs.size());
      
      Cab cab = cabs.iterator().next();
      assertEquals("1", cab.getCabId());
      assertEquals("1", cab.getDriverId());
      assertEquals("Toyota Innova", cab.getModel());
      assertEquals(1, cab.getCityId());
      assertEquals(Cab.CabStatus.AVAILABLE, cab.getStatus());
      assertNotNull(cab.getIdleFrom());
    }

    @Test
    void add_multipleCabs_shouldAssignUniqueIds() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      cabStore.add(2, 2, "Honda City", Cab.CabStatus.AVAILABLE);
      cabStore.add(3, 3, "Swift Dzire", Cab.CabStatus.AVAILABLE);
      
      var cabs = cabStore.cabs();
      assertEquals(3, cabs.size());
    }
  }

  @Nested
  class GetCabsTests {

    @Test
    void cabs_shouldReturnAllCabs() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      cabStore.add(2, 2, "Honda City", Cab.CabStatus.UNAVAILABLE);
      
      var cabs = cabStore.cabs();
      assertEquals(2, cabs.size());
    }

    @Test
    void cabs_emptyStore_shouldReturnEmptyCollection() {
      var cabs = cabStore.cabs();
      assertTrue(cabs.isEmpty());
    }
  }

  @Nested
  class ReserveMostSuitableCabTests {

    @Test
    void reserveMostSuitableCab_withAvailableCabs_shouldReserveCab() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      cabStore.add(2, 1, "Honda City", Cab.CabStatus.AVAILABLE);
      
      Cab reserved = cabStore.reserveMostSuitableCab(1, 2);
      
      assertNotNull(reserved);
      assertEquals(Cab.CabStatus.UNAVAILABLE, reserved.getStatus());
      assertEquals(2, reserved.getCityId());
    }

    @Test
    void reserveMostSuitableCab_noAvailableCabs_shouldThrowException() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.UNAVAILABLE);
      
      assertThrows(CabNotAvailableException.class, 
          () -> cabStore.reserveMostSuitableCab(1, 2));
    }

    @Test
    void reserveMostSuitableCab_noCabsInCity_shouldThrowException() {
      cabStore.add(1, 2, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      
      assertThrows(CabNotAvailableException.class, 
          () -> cabStore.reserveMostSuitableCab(1, 2));
    }

    @Test
    void reserveMostSuitableCab_shouldSelectOldestIdleCab() throws InterruptedException {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      Thread.sleep(10);
      cabStore.add(2, 1, "Honda City", Cab.CabStatus.AVAILABLE);
      
      Cab reserved = cabStore.reserveMostSuitableCab(1, null);
      
      assertEquals("1", reserved.getCabId());
    }
  }

  @Nested
  class UpdateCabTests {

    @Test
    void update_withValidCabId_shouldUpdateStatus() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      
      cabStore.update(1, null, Cab.CabStatus.UNAVAILABLE, System.currentTimeMillis());
      
      Cab cab = cabStore.cabs().iterator().next();
      assertEquals(Cab.CabStatus.UNAVAILABLE, cab.getStatus());
    }

    @Test
    void update_withValidCabId_shouldUpdateCityId() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      
      cabStore.update(1, 2, null, null);
      
      Cab cab = cabStore.cabs().iterator().next();
      assertEquals(2, cab.getCityId());
    }

    @Test
    void update_withInvalidCabId_shouldThrowException() {
      assertThrows(NotFoundException.class, 
          () -> cabStore.update(999, null, Cab.CabStatus.AVAILABLE, null));
    }

    @Test
    void update_shouldUpdateIdleFrom() {
      cabStore.add(1, 1, "Toyota Innova", Cab.CabStatus.AVAILABLE);
      long idleFrom = System.currentTimeMillis();
      
      cabStore.update(1, null, Cab.CabStatus.UNAVAILABLE, idleFrom);
      
      Cab cab = cabStore.cabs().iterator().next();
      assertEquals(idleFrom, cab.getIdleFrom());
    }
  }

  @Nested
  class SelectionStrategyTests {

    @Test
    void getSelectionStrategy_shouldReturnDefaultIdleTimeStrategy() {
      CabSelectionStrategy strategy = cabStore.getSelectionStrategy();
      assertInstanceOf(IdleTimeSelectionStrategy.class, strategy);
    }

    @Test
    void setSelectionStrategy_shouldChangeStrategy() {
      cabStore.setSelectionStrategy(new DistanceBasedSelectionStrategy());
      
      CabSelectionStrategy strategy = cabStore.getSelectionStrategy();
      assertInstanceOf(DistanceBasedSelectionStrategy.class, strategy);
    }

    @Test
    void reserveMostSuitableCab_withPickupLocation_shouldUseStrategy() {
      cabStore.setSelectionStrategy(new DistanceBasedSelectionStrategy());
      
      cabStore.add(1, 1, "Toyota", Cab.CabStatus.AVAILABLE);
      cabStore.add(2, 1, "Honda", Cab.CabStatus.AVAILABLE);
      
      Location pickupLocation = new Location(10, 10);
      Cab reserved = cabStore.reserveMostSuitableCab(1, 2, pickupLocation);
      
      assertNotNull(reserved);
    }
  }
}

package in.rsh.cab.user.store;

import in.rsh.cab.commons.model.Cab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenericStoreTest {

  private GenericStore<Cab> store;

  @BeforeEach
  void setUp() {
    store = new GenericStore<>();
  }

  @Nested
  class PutTests {

    @Test
    void put_shouldStoreObject() {
      Cab cab = Cab.builder().cabId("1").build();
      
      store.put("1", cab);
      
      assertNotNull(store.get("1"));
      assertEquals("1", store.get("1").getCabId());
    }

    @Test
    void put_shouldUpdateExistingObject() {
      Cab cab1 = Cab.builder().cabId("1").driverId("D1").build();
      Cab cab2 = Cab.builder().cabId("1").driverId("D2").build();
      
      store.put("1", cab1);
      store.put("1", cab2);
      
      assertEquals("D2", store.get("1").getDriverId());
    }
  }

  @Nested
  class GetTests {

    @Test
    void get_withExistingId_shouldReturnObject() {
      Cab cab = Cab.builder().cabId("1").build();
      store.put("1", cab);
      
      Cab result = store.get("1");
      
      assertNotNull(result);
      assertEquals("1", result.getCabId());
    }

    @Test
    void get_withNonExistingId_shouldReturnNull() {
      Cab result = store.get("nonexistent");
      
      assertNull(result);
    }
  }

  @Nested
  class GetAllTests {

    @Test
    void getAll_shouldReturnAllObjects() {
      store.put("1", Cab.builder().cabId("1").build());
      store.put("2", Cab.builder().cabId("2").build());
      store.put("3", Cab.builder().cabId("3").build());
      
      List<Cab> result = store.getAll();
      
      assertEquals(3, result.size());
    }

    @Test
    void getAll_emptyStore_shouldReturnEmptyList() {
      List<Cab> result = store.getAll();
      
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class UpdateTests {

    @Test
    void update_shouldUpdateObject() {
      Cab cab = Cab.builder().cabId("1").driverId("D1").build();
      store.put("1", cab);
      
      Cab updatedCab = Cab.builder().cabId("1").driverId("D2").build();
      store.update("1", updatedCab);
      
      assertEquals("D2", store.get("1").getDriverId());
    }
  }
}

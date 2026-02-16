package in.rsh.cab.commons.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CabEntityTest {

  @Test
  void testCabEntityCreation() {
    CabEntity entity = CabEntity.builder()
        .id(1)
        .cabId("CAB001")
        .driverId("DRIVER001")
        .cabNumber("KA01AB1234")
        .status(CabEntity.CabStatus.AVAILABLE)
        .type(CabEntity.CabType.S)
        .locationX(10)
        .locationY(20)
        .idleFrom(System.currentTimeMillis())
        .cityId(1)
        .model("Toyota Innova")
        .build();

    assertEquals(1, entity.getId());
    assertEquals("CAB001", entity.getCabId());
    assertEquals("DRIVER001", entity.getDriverId());
    assertEquals("KA01AB1234", entity.getCabNumber());
    assertEquals(CabEntity.CabStatus.AVAILABLE, entity.getStatus());
    assertEquals(CabEntity.CabType.S, entity.getType());
    assertEquals(10, entity.getLocationX());
    assertEquals(20, entity.getLocationY());
    assertEquals(1, entity.getCityId());
    assertEquals("Toyota Innova", entity.getModel());
  }

  @Test
  void testCabEntityNoArgsConstructor() {
    CabEntity entity = new CabEntity();
    assertNull(entity.getId());
    assertNull(entity.getCabId());
  }

  @Test
  void testCabEntityAllArgsConstructor() {
    CabEntity entity = new CabEntity(
        1, "CAB001", "DRIVER001", "KA01AB1234",
        CabEntity.CabStatus.AVAILABLE, CabEntity.CabType.M,
        10, 20, System.currentTimeMillis(), 1, "Honda City"
    );

    assertEquals(1, entity.getId());
    assertEquals("CAB001", entity.getCabId());
    assertEquals(CabEntity.CabType.M, entity.getType());
  }

  @Test
  void testCabEntitySetters() {
    CabEntity entity = new CabEntity();
    entity.setId(1);
    entity.setCabId("CAB001");
    entity.setDriverId("DRIVER001");
    entity.setStatus(CabEntity.CabStatus.ON_RIDE);
    entity.setCityId(2);
    entity.setIdleFrom(12345L);

    assertEquals(1, entity.getId());
    assertEquals("CAB001", entity.getCabId());
    assertEquals(CabEntity.CabStatus.ON_RIDE, entity.getStatus());
    assertEquals(2, entity.getCityId());
    assertEquals(12345L, entity.getIdleFrom());
  }

  @Test
  void testCabEntityToBuilder() {
    CabEntity entity = CabEntity.builder()
        .id(1)
        .cabId("CAB001")
        .status(CabEntity.CabStatus.AVAILABLE)
        .build();

    CabEntity modified = entity.toBuilder()
        .status(CabEntity.CabStatus.UNAVAILABLE)
        .build();

    assertEquals(1, modified.getId());
    assertEquals(CabEntity.CabStatus.UNAVAILABLE, modified.getStatus());
  }

  @Test
  void testCabStatusEnum() {
    assertEquals(3, CabEntity.CabStatus.values().length);
    assertEquals(CabEntity.CabStatus.AVAILABLE, CabEntity.CabStatus.valueOf("AVAILABLE"));
    assertEquals(CabEntity.CabStatus.UNAVAILABLE, CabEntity.CabStatus.valueOf("UNAVAILABLE"));
    assertEquals(CabEntity.CabStatus.ON_RIDE, CabEntity.CabStatus.valueOf("ON_RIDE"));
  }

  @Test
  void testCabTypeEnum() {
    assertEquals(3, CabEntity.CabType.values().length);
    assertEquals(CabEntity.CabType.S, CabEntity.CabType.valueOf("S"));
    assertEquals(CabEntity.CabType.M, CabEntity.CabType.valueOf("M"));
    assertEquals(CabEntity.CabType.L, CabEntity.CabType.valueOf("L"));
  }
}

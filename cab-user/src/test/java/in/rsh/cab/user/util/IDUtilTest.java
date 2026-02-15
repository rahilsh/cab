package in.rsh.cab.user.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IDUtilTest {

  @Test
  void generateID_shouldReturnNonNullString() {
    String id = IDUtil.generateID();
    
    assertNotNull(id);
    assertFalse(id.isEmpty());
  }

  @Test
  void generateID_shouldReturnUniqueIds() {
    String id1 = IDUtil.generateID();
    String id2 = IDUtil.generateID();
    
    assertNotEquals(id1, id2);
  }

  @Test
  void generateID_shouldReturnUUIDFormat() {
    String id = IDUtil.generateID();
    
    assertEquals(36, id.length());
    assertTrue(id.contains("-"));
  }
}

package in.rsh.cab.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IDUtilTest {

  @Test
  void generateID_shouldReturnNonNullUUID() {
    String id = IDUtil.generateID();
    assertNotNull(id);
    assertTrue(id.length() > 0);
  }

  @Test
  void generateID_shouldReturnUniqueIDs() {
    String id1 = IDUtil.generateID();
    String id2 = IDUtil.generateID();
    assertNotNull(id1);
    assertNotNull(id2);
    assertTrue(!id1.equals(id2));
  }
}

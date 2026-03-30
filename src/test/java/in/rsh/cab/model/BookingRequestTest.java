package in.rsh.cab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BookingRequestTest {

  @Test
  void create_withAllParameters_shouldStoreValues() {
    BookingRequest request = new BookingRequest("emp1", 1, 2, "key1");

    assertEquals("emp1", request.employeeId());
    assertEquals(1, request.fromCity());
    assertEquals(2, request.toCity());
    assertEquals("key1", request.idempotencyKey());
  }

  @Test
  void create_withNullOptionalFields_shouldAllowNulls() {
    BookingRequest request = new BookingRequest("emp1", 1, 2, null);

    assertEquals("emp1", request.employeeId());
    assertEquals(1, request.fromCity());
    assertEquals(2, request.toCity());
    assertNull(request.idempotencyKey());
  }

  @Test
  void create_withNoIdempotencyKey_shouldWork() {
    BookingRequest request = new BookingRequest("emp1", 1, 2, null);

    assertEquals("emp1", request.employeeId());
    assertEquals(1, request.fromCity());
    assertEquals(2, request.toCity());
  }
}

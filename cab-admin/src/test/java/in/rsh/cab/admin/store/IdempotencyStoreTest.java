package in.rsh.cab.admin.store;

import in.rsh.cab.commons.model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyStoreTest {

  private IdempotencyStore idempotencyStore;

  @BeforeEach
  void setUp() {
    idempotencyStore = new IdempotencyStore();
  }

  private Booking createBooking() {
    return Booking.builder()
        .bookingId("123")
        .cabId("1")
        .riderId("1")
        .startTime(LocalDateTime.now())
        .status(Booking.BookingStatus.IN_PROGRESS)
        .build();
  }

  @Nested
  class WithIdempotencyTests {

    @Test
    void withIdempotency_firstCall_shouldExecuteSupplier() {
      IdempotencyStore.BookingRequest request = 
          new IdempotencyStore.BookingRequest(1, 1, 2);
      
      Booking result = idempotencyStore.withIdempotency(
          "key1", request, () -> createBooking());
      
      assertNotNull(result);
      assertEquals("123", result.getBookingId());
    }

    @Test
    void withIdempotency_sameKeySameRequest_shouldReturnCachedResult() {
      IdempotencyStore.BookingRequest request = 
          new IdempotencyStore.BookingRequest(1, 1, 2);
      
      Booking result1 = idempotencyStore.withIdempotency(
          "key1", request, () -> createBooking());
      Booking result2 = idempotencyStore.withIdempotency(
          "key1", request, () -> createBooking());
      
      assertSame(result1, result2);
    }

    @Test
    void withIdempotency_sameKeyDifferentRequest_shouldThrowException() {
      IdempotencyStore.BookingRequest request1 = 
          new IdempotencyStore.BookingRequest(1, 1, 2);
      IdempotencyStore.BookingRequest request2 = 
          new IdempotencyStore.BookingRequest(1, 1, 3);
      
      idempotencyStore.withIdempotency("key1", request1, () -> createBooking());
      
      assertThrows(IllegalArgumentException.class, 
          () -> idempotencyStore.withIdempotency("key1", request2, () -> createBooking()));
    }

    @Test
    void withIdempotency_differentKeys_shouldExecuteSupplierEachTime() {
      IdempotencyStore.BookingRequest request1 = 
          new IdempotencyStore.BookingRequest(1, 1, 2);
      IdempotencyStore.BookingRequest request2 = 
          new IdempotencyStore.BookingRequest(2, 1, 2);
      
      Booking result1 = idempotencyStore.withIdempotency(
          "key1", request1, () -> createBooking());
      Booking result2 = idempotencyStore.withIdempotency(
          "key2", request2, () -> createBooking());
      
      assertNotSame(result1, result2);
    }

    @Test
    void withIdempotency_concurrentCallsSameKey_shouldBeThreadSafe() 
        throws InterruptedException {
      IdempotencyStore.BookingRequest request = 
          new IdempotencyStore.BookingRequest(1, 1, 2);
      
      Booking[] results = new Booking[2];
      Thread t1 = new Thread(() -> {
        results[0] = idempotencyStore.withIdempotency(
            "key1", request, () -> createBooking());
      });
      Thread t2 = new Thread(() -> {
        results[1] = idempotencyStore.withIdempotency(
            "key1", request, () -> createBooking());
      });
      
      t1.start();
      t2.start();
      t1.join();
      t2.join();
      
      assertSame(results[0], results[1]);
    }
  }
}

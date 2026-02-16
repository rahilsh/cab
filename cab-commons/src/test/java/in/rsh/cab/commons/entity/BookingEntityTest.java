package in.rsh.cab.commons.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingEntityTest {

  @Test
  void testBookingEntityCreation() {
    LocalDateTime now = LocalDateTime.now();
    BookingEntity entity = BookingEntity.builder()
        .id(1)
        .bookingId("BOOK001")
        .startTime(now)
        .endTime(now.plusHours(1))
        .riderId("RIDER001")
        .cabId("CAB001")
        .status(BookingEntity.BookingStatus.IN_PROGRESS)
        .startLocationX(10)
        .startLocationY(20)
        .endLocationX(30)
        .endLocationY(40)
        .bookedBy("EMP001")
        .build();

    assertEquals(1, entity.getId());
    assertEquals("BOOK001", entity.getBookingId());
    assertEquals(now, entity.getStartTime());
    assertEquals(now.plusHours(1), entity.getEndTime());
    assertEquals("RIDER001", entity.getRiderId());
    assertEquals("CAB001", entity.getCabId());
    assertEquals(BookingEntity.BookingStatus.IN_PROGRESS, entity.getStatus());
    assertEquals(10, entity.getStartLocationX());
    assertEquals(20, entity.getStartLocationY());
    assertEquals(30, entity.getEndLocationX());
    assertEquals(40, entity.getEndLocationY());
    assertEquals("EMP001", entity.getBookedBy());
  }

  @Test
  void testBookingEntityNoArgsConstructor() {
    BookingEntity entity = new BookingEntity();
    assertNull(entity.getId());
    assertNull(entity.getBookingId());
  }

  @Test
  void testBookingEntityAllArgsConstructor() {
    LocalDateTime now = LocalDateTime.now();
    BookingEntity entity = new BookingEntity(
        1, "BOOK001", now, now.plusHours(1), "RIDER001", "CAB001",
        BookingEntity.BookingStatus.COMPLETED,
        10, 20, 30, 40, "EMP001"
    );

    assertEquals(1, entity.getId());
    assertEquals(BookingEntity.BookingStatus.COMPLETED, entity.getStatus());
  }

  @Test
  void testBookingEntitySetters() {
    BookingEntity entity = new BookingEntity();
    entity.setId(1);
    entity.setBookingId("BOOK001");
    entity.setRiderId("RIDER001");
    entity.setCabId("CAB001");
    entity.setStatus(BookingEntity.BookingStatus.CANCELLED);
    entity.setBookedBy("EMP001");

    assertEquals(1, entity.getId());
    assertEquals("BOOK001", entity.getBookingId());
    assertEquals(BookingEntity.BookingStatus.CANCELLED, entity.getStatus());
  }

  @Test
  void testBookingStatusEnum() {
    assertEquals(3, BookingEntity.BookingStatus.values().length);
    assertEquals(BookingEntity.BookingStatus.IN_PROGRESS, BookingEntity.BookingStatus.valueOf("IN_PROGRESS"));
    assertEquals(BookingEntity.BookingStatus.COMPLETED, BookingEntity.BookingStatus.valueOf("COMPLETED"));
    assertEquals(BookingEntity.BookingStatus.CANCELLED, BookingEntity.BookingStatus.valueOf("CANCELLED"));
  }
}

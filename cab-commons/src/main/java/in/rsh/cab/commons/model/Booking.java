package in.rsh.cab.commons.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class Booking {

  private final String bookingId;
  private final LocalDateTime startTime;
  private final LocalDateTime endTime;
  private final String riderId;
  private final String cabId;
  private final BookingStatus status;
  private final Location startLocation;
  private final Location endLocation;
  private final String bookedBy;

  public enum BookingStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
  }
}

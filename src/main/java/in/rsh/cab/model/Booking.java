package in.rsh.cab.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Booking {

  private final Integer bookingId;
  private final LocalDateTime startTime;
  private final LocalDateTime endTime;
  private final String riderId;
  private final int cabId;
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

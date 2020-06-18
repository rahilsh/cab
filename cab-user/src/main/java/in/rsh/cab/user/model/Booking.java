package in.rsh.cab.user.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Booking {

  private final String bookingId;
  private final LocalDateTime startTime;
  private final LocalDateTime endTime;
  private final String riderId;
  private final String cabId;
  private final BookingStatus status;

  public enum BookingStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
  }
}

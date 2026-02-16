package in.rsh.cab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer bookingId;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(name = "rider_id")
  private String riderId;

  @Column(name = "cab_id")
  private int cabId;

  @Enumerated(EnumType.STRING)
  private BookingStatus status;

  @Column(name = "start_location_x")
  private Integer startLocationX;

  @Column(name = "start_location_y")
  private Integer startLocationY;

  @Column(name = "end_location_x")
  private Integer endLocationX;

  @Column(name = "end_location_y")
  private Integer endLocationY;

  @Column(name = "booked_by")
  private String bookedBy;

  public enum BookingStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
  }
}

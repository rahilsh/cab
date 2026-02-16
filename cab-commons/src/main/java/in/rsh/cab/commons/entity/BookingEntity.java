package in.rsh.cab.commons.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
  private Integer id;

  @Column(name = "booking_id", unique = true, nullable = false)
  private String bookingId;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(name = "rider_id")
  private String riderId;

  @Column(name = "cab_id")
  private String cabId;

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

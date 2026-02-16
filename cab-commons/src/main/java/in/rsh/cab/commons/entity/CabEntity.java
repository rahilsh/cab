package in.rsh.cab.commons.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cabs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CabEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "cab_id", unique = true, nullable = false)
  private String cabId;

  @Column(name = "driver_id", nullable = false)
  private String driverId;

  @Column(name = "cab_number")
  private String cabNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CabStatus status;

  @Enumerated(EnumType.STRING)
  private CabType type;

  private Integer locationX;
  private Integer locationY;

  @Column(name = "idle_from")
  private Long idleFrom;

  @Column(name = "city_id")
  private Integer cityId;

  private String model;

  public enum CabStatus {
    AVAILABLE,
    UNAVAILABLE,
    ON_RIDE
  }

  public enum CabType {
    S,
    M,
    L
  }
}

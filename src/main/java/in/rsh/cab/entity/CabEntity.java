package in.rsh.cab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

package in.rsh.cab.user.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "riders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RiderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "person_id", unique = true, nullable = false)
  private String personId;

  private String name;
  private String email;

  @Column(name = "phone_number")
  private String phoneNumber;

  @Column(name = "photo_url")
  private String photoURL;

  @Column(name = "current_location_x")
  private Integer currentLocationX;

  @Column(name = "current_location_y")
  private Integer currentLocationY;

  @Column(name = "home_x")
  private Integer homeX;

  @Column(name = "home_y")
  private Integer homeY;

  @Column(name = "work_x")
  private Integer workX;

  @Column(name = "work_y")
  private Integer workY;
}

package in.rsh.cab.commons.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cities")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CityEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(unique = true, nullable = false)
  private String name;

  public CityEntity(String name) {
    this.name = name;
  }
}

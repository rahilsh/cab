package in.rsh.cab.tenancy.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor
public class TenantEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(nullable = false)
  private String status;

  @Column(name = "default_currency", nullable = false, length = 3)
  private String defaultCurrency;

  @Column(nullable = false)
  private String timezone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public TenantEntity(
      UUID id,
      String slug,
      String displayName,
      String defaultCurrency,
      String timezone,
      Instant now) {
    this.id = id;
    this.slug = slug;
    this.displayName = displayName;
    this.status = "ACTIVE";
    this.defaultCurrency = defaultCurrency;
    this.timezone = timezone;
    this.createdAt = now;
    this.updatedAt = now;
  }
}

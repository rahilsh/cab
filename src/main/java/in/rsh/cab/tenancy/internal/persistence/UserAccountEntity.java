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
@Table(name = "user_accounts")
@Getter
@NoArgsConstructor
public class UserAccountEntity {

  @Id private UUID id;
  @Column(nullable = false) private String issuer;
  @Column(nullable = false) private String subject;
  private String email;
  @Column(name = "display_name") private String displayName;
  @Column(nullable = false) private String status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public UserAccountEntity(
      UUID id, String issuer, String subject, String email, String displayName, Instant now) {
    this.id = id;
    this.issuer = issuer;
    this.subject = subject;
    this.email = email;
    this.displayName = displayName;
    this.status = "ACTIVE";
    this.createdAt = now;
    this.updatedAt = now;
  }
}

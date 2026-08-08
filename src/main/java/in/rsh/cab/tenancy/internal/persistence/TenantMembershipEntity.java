package in.rsh.cab.tenancy.internal.persistence;

import in.rsh.cab.tenancy.TenantRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_memberships")
@Getter
@NoArgsConstructor
public class TenantMembershipEntity {

  @Id private UUID id;
  @Column(name = "tenant_id", nullable = false) private UUID tenantId;
  @Column(name = "user_account_id", nullable = false) private UUID userAccountId;
  @Column(nullable = false) private String status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  @ElementCollection
  @CollectionTable(
      name = "tenant_membership_roles",
      joinColumns = @JoinColumn(name = "membership_id"))
  @Column(name = "role", nullable = false)
  @Enumerated(EnumType.STRING)
  private Set<TenantRole> roles = new HashSet<>();

  public TenantMembershipEntity(
      UUID id, UUID tenantId, UUID userAccountId, Set<TenantRole> roles, Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.userAccountId = userAccountId;
    this.status = "ACTIVE";
    this.roles.addAll(roles);
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void addRole(TenantRole role, Instant now) {
    if (roles.add(role)) {
      updatedAt = now;
    }
  }
}

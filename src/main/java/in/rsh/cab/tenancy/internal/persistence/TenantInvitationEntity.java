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
@Table(name = "tenant_membership_invitations")
@Getter
@NoArgsConstructor
public class TenantInvitationEntity {

  @Id private UUID id;
  @Column(name = "tenant_id", nullable = false) private UUID tenantId;
  @Column(nullable = false) private String email;
  @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
  @Column(nullable = false) private String status;
  @Column(name = "invited_by_account_id", nullable = false) private UUID invitedByAccountId;
  @Column(name = "accepted_by_account_id") private UUID acceptedByAccountId;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "accepted_at") private Instant acceptedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  @ElementCollection
  @CollectionTable(
      name = "tenant_membership_invitation_roles",
      joinColumns = @JoinColumn(name = "invitation_id"))
  @Column(name = "role", nullable = false)
  @Enumerated(EnumType.STRING)
  private Set<TenantRole> roles = new HashSet<>();

  public TenantInvitationEntity(
      UUID id,
      UUID tenantId,
      String email,
      String tokenHash,
      Set<TenantRole> roles,
      UUID invitedByAccountId,
      Instant expiresAt,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.status = "INVITED";
    this.roles.addAll(roles);
    this.invitedByAccountId = invitedByAccountId;
    this.expiresAt = expiresAt;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public boolean canBeAcceptedAt(Instant now) {
    return "INVITED".equals(status) && expiresAt.isAfter(now);
  }

  public void accept(UUID accountId, Instant now) {
    status = "ACCEPTED";
    acceptedByAccountId = accountId;
    acceptedAt = now;
    updatedAt = now;
  }

  public void revoke(Instant now) {
    if ("INVITED".equals(status)) {
      status = "REVOKED";
      updatedAt = now;
    }
  }
}

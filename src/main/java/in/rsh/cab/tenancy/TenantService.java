package in.rsh.cab.tenancy;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.tenancy.internal.persistence.TenantEntity;
import in.rsh.cab.tenancy.internal.persistence.TenantMembershipEntity;
import in.rsh.cab.tenancy.internal.persistence.TenantMembershipRepository;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import in.rsh.cab.tenancy.internal.persistence.UserAccountEntity;
import in.rsh.cab.tenancy.internal.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

  private final TenantRepository tenants;
  private final UserAccountRepository accounts;
  private final TenantMembershipRepository memberships;
  private final Clock clock;

  public TenantService(
      TenantRepository tenants,
      UserAccountRepository accounts,
      TenantMembershipRepository memberships,
      Clock clock) {
    this.tenants = tenants;
    this.accounts = accounts;
    this.memberships = memberships;
    this.clock = clock;
  }

  @Transactional
  public TenantSummary create(
      String issuer,
      String subject,
      String email,
      String actorName,
      String slug,
      String displayName,
      String currency,
      String timezone) {
    if (tenants.existsBySlug(slug)) {
      throw new InvalidRequestException("Tenant slug is already in use");
    }
    Instant now = clock.instant();
    UserAccountEntity account =
        accounts
            .findByIssuerAndSubject(issuer, subject)
            .orElseGet(
                () ->
                    accounts.save(
                        new UserAccountEntity(
                            UUID.randomUUID(), issuer, subject, email, actorName, now)));
    TenantEntity tenant =
        tenants.save(
            new TenantEntity(
                UUID.randomUUID(), slug, displayName, currency.toUpperCase(), timezone, now));
    Set<TenantRole> roles = Set.of(TenantRole.TENANT_ADMIN);
    memberships.save(
        new TenantMembershipEntity(
            UUID.randomUUID(), tenant.getId(), account.getId(), roles, now));
    return summary(tenant, roles);
  }

  @Transactional(readOnly = true)
  public List<TenantSummary> findForIdentity(String issuer, String subject) {
    return accounts
        .findByIssuerAndSubject(issuer, subject)
        .map(
            account ->
                memberships.findByUserAccountIdAndStatus(account.getId(), "ACTIVE").stream()
                    .map(
                        membership ->
                            tenants
                                .findById(membership.getTenantId())
                                .map(tenant -> summary(tenant, membership.getRoles()))
                                .orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList())
        .orElseGet(List::of);
  }

  @Transactional(readOnly = true)
  public TenantContext authorize(String issuer, String subject, UUID tenantId) {
    UserAccountEntity account =
        accounts
            .findByIssuerAndSubject(issuer, subject)
            .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
            .orElseThrow(() -> new TenantAccessDeniedException("Active account is required"));
    TenantMembershipEntity membership =
        memberships
            .findByTenantIdAndUserAccountIdAndStatus(tenantId, account.getId(), "ACTIVE")
            .orElseThrow(() -> new TenantAccessDeniedException("Active tenant membership is required"));
    return new TenantContext(
        tenantId, account.getId(), membership.getId(), membership.getRoles());
  }

  @Transactional
  public void grantRoleForActiveAccount(UUID accountId, TenantRole role) {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.TENANT_ADMIN)) {
      throw new TenantAccessDeniedException("TENANT_ADMIN role is required");
    }
    TenantMembershipEntity membership =
        memberships
            .findByTenantIdAndUserAccountIdAndStatus(context.tenantId(), accountId, "ACTIVE")
            .orElseThrow(
                () -> new InvalidRequestException("Account must have an active tenant membership"));
    membership.addRole(role, clock.instant());
  }

  @Transactional
  public void grantSelfServiceRole(TenantRole role) {
    if (role != TenantRole.RIDER && role != TenantRole.DRIVER) {
      throw new InvalidRequestException("Only RIDER or DRIVER can be granted here");
    }
    grantRoleForActiveAccount(TenantContext.require().accountId(), role);
  }

  private TenantSummary summary(TenantEntity tenant, Set<TenantRole> roles) {
    return new TenantSummary(
        tenant.getId(), tenant.getSlug(), tenant.getDisplayName(), tenant.getStatus(), Set.copyOf(roles));
  }
}

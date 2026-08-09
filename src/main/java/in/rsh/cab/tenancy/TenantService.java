package in.rsh.cab.tenancy;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.tenancy.internal.persistence.TenantEntity;
import in.rsh.cab.tenancy.internal.persistence.TenantInvitationEntity;
import in.rsh.cab.tenancy.internal.persistence.TenantInvitationRepository;
import in.rsh.cab.tenancy.internal.persistence.TenantMembershipEntity;
import in.rsh.cab.tenancy.internal.persistence.TenantMembershipRepository;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import in.rsh.cab.tenancy.internal.persistence.UserAccountEntity;
import in.rsh.cab.tenancy.internal.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
public class TenantService {

  private final TenantRepository tenants;
  private final UserAccountRepository accounts;
  private final TenantMembershipRepository memberships;
  private final TenantInvitationRepository invitations;
  private final Clock clock;
  private final Duration invitationTtl;
  private final SecureRandom secureRandom = new SecureRandom();

  public TenantService(
      TenantRepository tenants,
      UserAccountRepository accounts,
      TenantMembershipRepository memberships,
      TenantInvitationRepository invitations,
      Clock clock,
      @Value("${tenancy.invitation-ttl:PT72H}") Duration invitationTtl) {
    this.tenants = tenants;
    this.accounts = accounts;
    this.memberships = memberships;
    this.invitations = invitations;
    this.clock = clock;
    if (invitationTtl.isZero() || invitationTtl.isNegative()) {
      throw new IllegalArgumentException("Invitation TTL must be positive");
    }
    this.invitationTtl = invitationTtl;
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
    tenants
        .findById(tenantId)
        .filter(tenant -> "ACTIVE".equals(tenant.getStatus()))
        .orElseThrow(() -> new TenantAccessDeniedException("Active tenant is required"));
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
    if (role != TenantRole.RIDER) {
      throw new InvalidRequestException("Only RIDER can be self-granted");
    }
    TenantContext context = TenantContext.require();
    TenantMembershipEntity membership =
        memberships
            .findByTenantIdAndUserAccountIdAndStatus(
                context.tenantId(), context.accountId(), "ACTIVE")
            .orElseThrow(() -> new TenantAccessDeniedException("Active tenant membership is required"));
    membership.addRole(role, clock.instant());
  }

  @Transactional
  public TenantInvitation invite(String email, Set<TenantRole> roles) {
    TenantContext context = requireAdmin();
    Set<TenantRole> grantedRoles = validateOnboardingRoles(roles);
    String normalizedEmail = normalizeEmail(email);
    byte[] tokenBytes = new byte[32];
    secureRandom.nextBytes(tokenBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    Instant now = clock.instant();
    TenantInvitationEntity invitation =
        invitations.save(
            new TenantInvitationEntity(
                UUID.randomUUID(),
                context.tenantId(),
                normalizedEmail,
                hashToken(token),
                grantedRoles,
                context.accountId(),
                now.plus(invitationTtl),
                now));
    return invitation(invitation, token);
  }

  @Transactional
  public TenantMembership acceptInvitation(
      String token,
      String issuer,
      String subject,
      String email,
      String displayName,
      boolean emailVerified) {
    if (token == null || token.isBlank()) {
      throw new InvalidRequestException("Invitation token is required");
    }
    Instant now = clock.instant();
    TenantInvitationEntity invitation =
        invitations
            .findByTokenHash(hashToken(token))
            .filter(candidate -> candidate.canBeAcceptedAt(now))
            .orElseThrow(() -> new TenantAccessDeniedException("Invitation cannot be accepted"));
    if (!emailVerified) {
      throw new TenantAccessDeniedException("Invitation cannot be accepted");
    }
    String normalizedEmail = normalizeEmail(email);
    if (!MessageDigest.isEqual(
        invitation.getEmail().getBytes(StandardCharsets.UTF_8),
        normalizedEmail.getBytes(StandardCharsets.UTF_8))) {
      throw new TenantAccessDeniedException("Invitation cannot be accepted");
    }
    TenantEntity tenant =
        tenants
            .findById(invitation.getTenantId())
            .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
            .orElseThrow(() -> new TenantAccessDeniedException("Invitation cannot be accepted"));
    UserAccountEntity account =
        accounts
            .findByIssuerAndSubject(issuer, subject)
            .orElseGet(
                () ->
                    accounts.save(
                        new UserAccountEntity(
                            UUID.randomUUID(), issuer, subject, normalizedEmail, displayName, now)));
    if (!"ACTIVE".equals(account.getStatus())) {
      throw new TenantAccessDeniedException("Invitation cannot be accepted");
    }
    account.updateProfile(normalizedEmail, displayName, now);
    if (memberships
        .findByTenantIdAndUserAccountId(tenant.getId(), account.getId())
        .isPresent()) {
      throw new ConflictException("Tenant membership already exists");
    }
    TenantMembershipEntity membership =
        memberships.save(
            new TenantMembershipEntity(
                UUID.randomUUID(), tenant.getId(), account.getId(), invitation.getRoles(), now));
    invitation.accept(account.getId(), now);
    return membership(membership);
  }

  @Transactional
  public TenantMembership addExistingAccount(UUID accountId, Set<TenantRole> roles) {
    TenantContext context = requireAdmin();
    Set<TenantRole> grantedRoles = validateOnboardingRoles(roles);
    UserAccountEntity account =
        accounts
            .findById(accountId)
            .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
            .orElseThrow(() -> new InvalidRequestException("Active account is required"));
    if (memberships.findByTenantIdAndUserAccountId(context.tenantId(), accountId).isPresent()) {
      throw new ConflictException("Tenant membership already exists");
    }
    TenantMembershipEntity membership =
        memberships.save(
            new TenantMembershipEntity(
                UUID.randomUUID(), context.tenantId(), account.getId(), grantedRoles, clock.instant()));
    return membership(membership);
  }

  @Transactional
  public void revokeInvitation(UUID invitationId) {
    TenantContext context = requireAdmin();
    TenantInvitationEntity invitation =
        invitations
            .findByIdAndTenantId(invitationId, context.tenantId())
            .orElseThrow(() -> new InvalidRequestException("Invitation not found"));
    if (!"INVITED".equals(invitation.getStatus())) {
      throw new ConflictException("Invitation is no longer pending");
    }
    invitation.revoke(clock.instant());
  }

  private TenantContext requireAdmin() {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.TENANT_ADMIN)) {
      throw new TenantAccessDeniedException("TENANT_ADMIN role is required");
    }
    return context;
  }

  private Set<TenantRole> validateOnboardingRoles(Set<TenantRole> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new InvalidRequestException("At least one tenant role is required");
    }
    return Set.copyOf(roles);
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new InvalidRequestException("A verified identity email is required");
    }
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private String hashToken(String token) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private TenantInvitation invitation(TenantInvitationEntity entity, String token) {
    return new TenantInvitation(
        entity.getId(),
        entity.getTenantId(),
        entity.getEmail(),
        Set.copyOf(entity.getRoles()),
        entity.getStatus(),
        entity.getExpiresAt(),
        token);
  }

  private TenantMembership membership(TenantMembershipEntity entity) {
    return new TenantMembership(
        entity.getId(), entity.getTenantId(), entity.getUserAccountId(), Set.copyOf(entity.getRoles()));
  }

  private TenantSummary summary(TenantEntity tenant, Set<TenantRole> roles) {
    return new TenantSummary(
        tenant.getId(), tenant.getSlug(), tenant.getDisplayName(), tenant.getStatus(), Set.copyOf(roles));
  }
}

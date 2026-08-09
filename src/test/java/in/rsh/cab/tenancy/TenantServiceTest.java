package in.rsh.cab.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.InvalidRequestException;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantServiceTest {

  private final TenantRepository tenants = mock(TenantRepository.class);
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
  private final TenantInvitationRepository invitations = mock(TenantInvitationRepository.class);
  private TenantService service;

  @BeforeEach
  void setUp() {
    service =
        new TenantService(
            tenants,
            accounts,
            memberships,
            invitations,
            Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(72));
    when(tenants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(memberships.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(invitations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createsTenantAccountAndAdminMembership() {
    TenantSummary tenant =
        service.create(
            "https://issuer", "subject", "owner@example.com", "Owner", "city-cabs", "City Cabs", "usd", "UTC");

    assertEquals("city-cabs", tenant.slug());
    assertEquals(Set.of(TenantRole.TENANT_ADMIN), tenant.roles());
    verify(accounts).save(any(UserAccountEntity.class));
    verify(memberships).save(any(TenantMembershipEntity.class));
  }

  @Test
  void reusesExistingAccountAndRejectsDuplicateSlug() {
    UserAccountEntity account =
        new UserAccountEntity(UUID.randomUUID(), "issuer", "subject", null, null, Instant.now());
    when(accounts.findByIssuerAndSubject("issuer", "subject")).thenReturn(Optional.of(account));
    service.create("issuer", "subject", null, null, "one", "One", "EUR", "Europe/Paris");
    verify(accounts, org.mockito.Mockito.never()).save(any());

    when(tenants.existsBySlug("taken")).thenReturn(true);
    assertThrows(
        InvalidRequestException.class,
        () -> service.create("issuer", "subject", null, null, "taken", "Taken", "EUR", "UTC"));
  }

  @Test
  void listsOnlyMembershipTenantsAndHandlesUnknownIdentity() {
    UUID accountId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UserAccountEntity account =
        new UserAccountEntity(accountId, "issuer", "subject", null, null, Instant.now());
    TenantEntity tenant =
        new TenantEntity(tenantId, "one", "One", "USD", "UTC", Instant.now());
    TenantMembershipEntity membership =
        new TenantMembershipEntity(
            UUID.randomUUID(), tenantId, accountId, Set.of(TenantRole.DRIVER), Instant.now());
    when(accounts.findByIssuerAndSubject("issuer", "subject")).thenReturn(Optional.of(account));
    when(memberships.findByUserAccountIdAndStatus(accountId, "ACTIVE"))
        .thenReturn(List.of(membership));
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

    assertEquals(1, service.findForIdentity("issuer", "subject").size());
    when(accounts.findByIssuerAndSubject("issuer", "missing")).thenReturn(Optional.empty());
    assertEquals(List.of(), service.findForIdentity("issuer", "missing"));
  }

  @Test
  void authorizesOnlyActiveAccountMembership() {
    UUID accountId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UserAccountEntity account =
        new UserAccountEntity(accountId, "issuer", "subject", null, null, Instant.now());
    TenantMembershipEntity membership =
        new TenantMembershipEntity(
            UUID.randomUUID(), tenantId, accountId, Set.of(TenantRole.FINANCE), Instant.now());
    when(accounts.findByIssuerAndSubject("issuer", "subject")).thenReturn(Optional.of(account));
    when(tenants.findById(tenantId))
        .thenReturn(Optional.of(new TenantEntity(tenantId, "one", "One", "USD", "UTC", Instant.now())));
    when(memberships.findByTenantIdAndUserAccountIdAndStatus(tenantId, accountId, "ACTIVE"))
        .thenReturn(Optional.of(membership));

    assertEquals(tenantId, service.authorize("issuer", "subject", tenantId).tenantId());
    assertThrows(
        TenantAccessDeniedException.class,
        () -> service.authorize("issuer", "unknown", tenantId));
  }

  @Test
  void activeMemberCanSelfGrantOnlyRider() {
    UUID tenantId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    TenantMembershipEntity membership = new TenantMembershipEntity(
        UUID.randomUUID(), tenantId, accountId, Set.of(TenantRole.TENANT_ADMIN), Instant.EPOCH);
    TenantContext.set(new TenantContext(
        tenantId, accountId, membership.getId(), Set.of(TenantRole.TENANT_ADMIN)));
    when(memberships.findByTenantIdAndUserAccountIdAndStatus(tenantId, accountId, "ACTIVE"))
        .thenReturn(Optional.of(membership));

    service.grantSelfServiceRole(TenantRole.RIDER);
    assertEquals(Set.of(TenantRole.TENANT_ADMIN, TenantRole.RIDER), membership.getRoles());
    assertThrows(InvalidRequestException.class,
        () -> service.grantSelfServiceRole(TenantRole.FINANCE));
    assertThrows(InvalidRequestException.class,
        () -> service.grantSelfServiceRole(TenantRole.DRIVER));

    when(memberships.findByTenantIdAndUserAccountIdAndStatus(tenantId, UUID.randomUUID(), "ACTIVE"))
        .thenReturn(Optional.empty());
    assertThrows(InvalidRequestException.class,
        () -> service.grantRoleForActiveAccount(UUID.randomUUID(), TenantRole.DRIVER));
    TenantContext.clear();
  }

  @Test
  void nonAdminCannotGrantTenantRole() {
    TenantContext.set(new TenantContext(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(TenantRole.DRIVER)));
    assertThrows(TenantAccessDeniedException.class,
        () -> service.grantRoleForActiveAccount(UUID.randomUUID(), TenantRole.RIDER));
    TenantContext.clear();
  }

  @Test
  void tenantAdminInvitesAndStoresOnlyTokenHash() {
    UUID tenantId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    TenantContext.set(
        new TenantContext(
            tenantId, accountId, UUID.randomUUID(), Set.of(TenantRole.TENANT_ADMIN)));

    TenantInvitation invitation = service.invite(" New.Member@Example.COM ", Set.of(TenantRole.RIDER));

    assertEquals("new.member@example.com", invitation.email());
    assertEquals(43, invitation.token().length());
    verify(invitations)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                entity ->
                    entity.getTokenHash().length() == 64
                        && !entity.getTokenHash().contains(invitation.token())));
    TenantContext.clear();
  }

  @Test
  void acceptsInvitationForMatchingVerifiedIdentityOnce() {
    UUID tenantId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    TenantEntity tenant =
        new TenantEntity(tenantId, "one", "One", "USD", "UTC", Instant.EPOCH);
    TenantInvitationEntity invitation =
        new TenantInvitationEntity(
            UUID.randomUUID(),
            tenantId,
            "member@example.com",
            "unused",
            Set.of(TenantRole.RIDER),
            UUID.randomUUID(),
            Instant.parse("2026-08-09T00:00:00Z"),
            Instant.EPOCH);
    UserAccountEntity account =
        new UserAccountEntity(
            accountId, "https://issuer", "member", null, null, Instant.EPOCH);
    when(invitations.findByTokenHash(any())).thenReturn(Optional.of(invitation));
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(accounts.findByIssuerAndSubject("https://issuer", "member"))
        .thenReturn(Optional.of(account));
    when(memberships.findByTenantIdAndUserAccountId(tenantId, accountId))
        .thenReturn(Optional.empty());

    TenantMembership membership =
        service.acceptInvitation(
            "opaque-token",
            "https://issuer",
            "member",
            "MEMBER@example.com",
            "Member",
            true);

    assertEquals(Set.of(TenantRole.RIDER), membership.roles());
    assertEquals("ACCEPTED", invitation.getStatus());
    assertThrows(
        TenantAccessDeniedException.class,
        () ->
            service.acceptInvitation(
                "opaque-token",
                "https://issuer",
                "member",
                "member@example.com",
                "Member",
                true));
  }

  @Test
  void rejectsInvitationForUnverifiedOrDifferentEmailAndInactiveTenant() {
    TenantInvitationEntity invitation =
        new TenantInvitationEntity(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "member@example.com",
            "unused",
            Set.of(TenantRole.DRIVER),
            UUID.randomUUID(),
            Instant.parse("2026-08-09T00:00:00Z"),
            Instant.EPOCH);
    when(invitations.findByTokenHash(any())).thenReturn(Optional.of(invitation));

    assertThrows(
        TenantAccessDeniedException.class,
        () ->
            service.acceptInvitation(
                "token", "issuer", "subject", "member@example.com", null, false));
    assertThrows(
        TenantAccessDeniedException.class,
        () ->
            service.acceptInvitation(
                "token", "issuer", "subject", "other@example.com", null, true));
    when(tenants.findById(invitation.getTenantId())).thenReturn(Optional.empty());
    assertThrows(
        TenantAccessDeniedException.class,
        () ->
            service.acceptInvitation(
                "token", "issuer", "subject", "member@example.com", null, true));
  }

  @Test
  void authorizingInactiveTenantFailsBeforeMembershipLookup() {
    UUID tenantId = UUID.randomUUID();
    when(tenants.findById(tenantId)).thenReturn(Optional.empty());
    assertThrows(
        TenantAccessDeniedException.class,
        () -> service.authorize("issuer", "subject", tenantId));
  }
}

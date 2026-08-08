package in.rsh.cab.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.tenancy.internal.persistence.TenantEntity;
import in.rsh.cab.tenancy.internal.persistence.TenantMembershipEntity;
import in.rsh.cab.tenancy.internal.persistence.TenantMembershipRepository;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import in.rsh.cab.tenancy.internal.persistence.UserAccountEntity;
import in.rsh.cab.tenancy.internal.persistence.UserAccountRepository;
import java.time.Clock;
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
  private TenantService service;

  @BeforeEach
  void setUp() {
    service =
        new TenantService(
            tenants,
            accounts,
            memberships,
            Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));
    when(tenants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
}

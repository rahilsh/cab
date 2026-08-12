package in.rsh.cab.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.driver.internal.persistence.DriverProfileRepository;
import in.rsh.cab.driver.internal.persistence.DriverDocumentRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import in.rsh.cab.tenancy.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DriverServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private final DriverProfileRepository repository = mock(DriverProfileRepository.class);
  private final DriverDocumentRepository documents = mock(DriverDocumentRepository.class);
  private final TenantService tenants = mock(TenantService.class);
  private DriverService service;

  @BeforeEach
  void setUp() {
    service = new DriverService(
        repository, documents, tenants,
        Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC));
    admin();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void adminOnboardsListsAndApprovesMember() {
    DriverProfile pending = service.create(ACCOUNT_ID, "Driver One", "+1 555 0100");
    assertEquals(DriverStatus.PENDING, pending.status());
    verify(tenants).grantRoleForActiveAccount(ACCOUNT_ID, TenantRole.DRIVER);
    verify(repository).insert(TENANT_ID, pending);

    when(repository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(pending));
    assertEquals(List.of(pending), service.list());
    when(repository.findByTenantIdAndId(TENANT_ID, pending.id())).thenReturn(Optional.of(pending));
    when(documents.hasVerifiedDrivingLicense(
        TENANT_ID, pending.id(), java.time.LocalDate.parse("2026-08-08"))).thenReturn(true);
    when(repository.updateStatus(any(), any(), any(), any(), any())).thenReturn(true);
    assertEquals(DriverStatus.APPROVED, service.approve(pending.id()).status());
  }

  @Test
  void approvalRequiresVerifiedLicenseThatHasNotExpired() {
    DriverProfile pending = profile(DriverStatus.PENDING);
    when(repository.findByTenantIdAndId(TENANT_ID, pending.id())).thenReturn(Optional.of(pending));

    assertThrows(ConflictException.class, () -> service.approve(pending.id()));
    verify(documents).hasVerifiedDrivingLicense(
        TENANT_ID, pending.id(), java.time.LocalDate.parse("2026-08-08"));
  }

  @Test
  void adminSuspendsApprovedDriverAndRejectsInvalidOrConcurrentTransitions() {
    DriverProfile approved = profile(DriverStatus.APPROVED);
    when(repository.findByTenantIdAndId(TENANT_ID, approved.id())).thenReturn(Optional.of(approved));
    when(repository.updateStatus(any(), any(), any(), any(), any())).thenReturn(true);
    assertEquals(DriverStatus.SUSPENDED, service.suspend(approved.id()).status());

    DriverProfile pending = profile(DriverStatus.PENDING);
    when(repository.findByTenantIdAndId(TENANT_ID, pending.id())).thenReturn(Optional.of(pending));
    assertThrows(ConflictException.class, () -> service.suspend(pending.id()));

    when(repository.findByTenantIdAndId(TENANT_ID, approved.id())).thenReturn(Optional.of(approved));
    when(repository.updateStatus(any(), any(), any(), any(), any())).thenReturn(false);
    assertThrows(ConflictException.class, () -> service.suspend(approved.id()));
    assertThrows(NotFoundException.class, () -> service.approve(UUID.randomUUID()));
  }

  @Test
  void driverReadsAndUpdatesOwnProfile() {
    driver();
    DriverProfile profile = profile(DriverStatus.APPROVED);
    when(repository.findByTenantIdAndAccountId(TENANT_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(profile));
    assertEquals(profile, service.getOwn());
    when(repository.updateOwn(any(), any(), any(), any(), any())).thenReturn(true);
    assertEquals(profile, service.updateOwn("Driver One", null));

    when(repository.updateOwn(any(), any(), any(), any(), any())).thenReturn(false);
    assertThrows(NotFoundException.class, () -> service.updateOwn("Missing", null));
    when(repository.findByTenantIdAndAccountId(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, service::getOwn);
  }

  @Test
  void rejectsWrongRoleAndDuplicateProfile() {
    driver();
    assertThrows(TenantAccessDeniedException.class, service::list);
    admin();
    doThrow(new DataIntegrityViolationException("constraint"))
        .when(repository).insert(any(), any());
    assertThrows(ConflictException.class, () -> service.create(ACCOUNT_ID, "Driver", null));
  }

  private DriverProfile profile(DriverStatus status) {
    return new DriverProfile(
        UUID.randomUUID(), ACCOUNT_ID, "Driver One", null, status, Instant.EPOCH, Instant.EPOCH);
  }

  private void admin() {
    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.TENANT_ADMIN)));
  }

  private void driver() {
    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.DRIVER)));
  }
}

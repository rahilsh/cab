package in.rsh.cab.rider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.rider.internal.persistence.RiderProfileRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RiderProfileServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private final RiderProfileRepository repository = mock(RiderProfileRepository.class);
  private RiderProfileService service;

  @BeforeEach
  void setUp() {
    service = new RiderProfileService(
        repository, Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC));
    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.RIDER)));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createsAndReadsProfileForContextAccount() {
    RiderProfile created = service.create("Rider One", "+1 555 0100");
    when(repository.findByTenantIdAndAccountId(TENANT_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(created));

    assertEquals(Instant.parse("2026-08-08T10:00:00Z"), created.createdAt());
    assertEquals(created, service.getOwn());
    verify(repository).insert(TENANT_ID, ACCOUNT_ID, created);
  }

  @Test
  void updatesOnlyContextAccountAndReportsMissingProfile() {
    RiderProfile updated = new RiderProfile(
        UUID.randomUUID(), "New Name", null, Instant.EPOCH, Instant.EPOCH);
    when(repository.update(TENANT_ID, ACCOUNT_ID, "New Name", null,
        Instant.parse("2026-08-08T10:00:00Z"))).thenReturn(true);
    when(repository.findByTenantIdAndAccountId(TENANT_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(updated));
    assertEquals(updated, service.updateOwn("New Name", null));

    when(repository.update(any(), any(), any(), any(), any())).thenReturn(false);
    assertThrows(NotFoundException.class, () -> service.updateOwn("Missing", null));
    when(repository.findByTenantIdAndAccountId(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, service::getOwn);
  }

  @Test
  void requiresRiderAndMapsDuplicateToConflict() {
    doThrow(new DataIntegrityViolationException("constraint"))
        .when(repository).insert(any(), any(), any());
    assertThrows(ConflictException.class, () -> service.create("Rider", null));

    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.TENANT_ADMIN)));
    assertThrows(TenantAccessDeniedException.class, service::getOwn);
  }
}

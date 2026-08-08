package in.rsh.cab.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.driver.internal.persistence.DriverDocumentRepository;
import in.rsh.cab.driver.internal.persistence.DriverProfileRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverDocumentServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final UUID DRIVER = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final DriverDocumentRepository documents = mock(DriverDocumentRepository.class);
  private final DriverProfileRepository drivers = mock(DriverProfileRepository.class);
  private DriverDocumentService service;

  @BeforeEach
  void setUp() {
    service = new DriverDocumentService(documents, drivers, Clock.fixed(NOW, ZoneOffset.UTC));
    context(TenantRole.DRIVER);
    when(drivers.findByTenantIdAndAccountId(TENANT, ACCOUNT)).thenReturn(Optional.of(profile()));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void driverSubmitsAndListsSafeMetadata() {
    DriverDocument submitted = service.submit(DriverDocumentType.DRIVING_LICENSE, "license-42",
        "drivers/42/license.pdf", LocalDate.parse("2026-08-08"));
    assertEquals(DriverDocumentStatus.PENDING, submitted.verificationStatus());
    assertEquals(LocalDate.parse("2026-08-08"), submitted.expiresOn());
    verify(documents).insert(TENANT, submitted);
    verify(documents).appendHistory(TENANT, submitted.id(), null, DriverDocumentStatus.PENDING,
        ACCOUNT, null, NOW);

    when(documents.findAll(TENANT, DRIVER)).thenReturn(List.of(submitted));
    assertEquals(List.of(submitted), service.listOwn());
  }

  @Test
  void rejectsUrlsAndTraversal() {
    assertThrows(InvalidRequestException.class, () -> service.submit(
        DriverDocumentType.DRIVING_LICENSE, "ref", "https://objects.example/license",
        LocalDate.parse("2027-08-08")));
    assertThrows(InvalidRequestException.class, () -> service.submit(
        DriverDocumentType.DRIVING_LICENSE, "ref", "drivers/../secret",
        LocalDate.parse("2027-08-08")));
    assertThrows(InvalidRequestException.class, () -> service.submit(
        DriverDocumentType.DRIVING_LICENSE, "https://provider.example/ref", "safe/key",
        LocalDate.parse("2027-08-08")));
  }

  @Test
  void requiresCurrentDrivingLicenseMetadata() {
    assertThrows(InvalidRequestException.class, () -> service.submit(
        DriverDocumentType.DRIVING_LICENSE, "license", "drivers/license", null));
    assertThrows(InvalidRequestException.class, () -> service.submit(
        DriverDocumentType.DRIVING_LICENSE, "license", "drivers/license",
        LocalDate.parse("2026-08-07")));
  }

  @Test
  void adminVerifiesOrRejectsPendingDocumentOnce() {
    context(TenantRole.TENANT_ADMIN);
    DriverDocument pending = document(DriverDocumentStatus.PENDING, null);
    DriverDocument verified = reviewed(pending, DriverDocumentStatus.VERIFIED, null);
    when(documents.find(TENANT, DRIVER, pending.id()))
        .thenReturn(Optional.of(pending), Optional.of(verified));
    when(documents.review(TENANT, DRIVER, pending.id(), DriverDocumentStatus.VERIFIED,
        ACCOUNT, NOW, null, NOW)).thenReturn(true);
    assertEquals(DriverDocumentStatus.VERIFIED,
        service.verify(DRIVER, pending.id()).verificationStatus());

    DriverDocument pendingRejection = document(DriverDocumentStatus.PENDING, null);
    DriverDocument rejected = reviewed(pendingRejection, DriverDocumentStatus.REJECTED, "Unreadable");
    when(documents.find(TENANT, DRIVER, pendingRejection.id()))
        .thenReturn(Optional.of(pendingRejection), Optional.of(rejected));
    when(documents.review(TENANT, DRIVER, pendingRejection.id(), DriverDocumentStatus.REJECTED,
        ACCOUNT, null, "Unreadable", NOW)).thenReturn(true);
    assertEquals("Unreadable",
        service.reject(DRIVER, pendingRejection.id(), "Unreadable").rejectionReason());

    when(documents.find(TENANT, DRIVER, pending.id())).thenReturn(Optional.of(pending));
    when(documents.review(TENANT, DRIVER, pending.id(), DriverDocumentStatus.VERIFIED,
        ACCOUNT, NOW, null, NOW)).thenReturn(false);
    assertThrows(ConflictException.class, () -> service.verify(DRIVER, pending.id()));
  }

  @Test
  void enforcesWorkflowRolesAndRejectionReason() {
    assertThrows(TenantAccessDeniedException.class, () -> service.list(DRIVER));
    context(TenantRole.TENANT_ADMIN);
    assertThrows(InvalidRequestException.class,
        () -> service.reject(DRIVER, UUID.randomUUID(), " "));
  }

  private DriverProfile profile() {
    return new DriverProfile(DRIVER, ACCOUNT, "Driver", null, DriverStatus.PENDING, NOW, NOW);
  }

  private DriverDocument document(DriverDocumentStatus status, String reason) {
    return new DriverDocument(UUID.randomUUID(), DRIVER, DriverDocumentType.DRIVING_LICENSE,
        "license-42", "drivers/42/license.pdf", LocalDate.parse("2027-08-08"), status,
        null, null, reason, NOW, NOW);
  }

  private DriverDocument reviewed(
      DriverDocument document, DriverDocumentStatus status, String reason) {
    return new DriverDocument(document.id(), document.driverId(), document.documentType(),
        document.documentReference(), document.objectKey(), document.expiresOn(), status, ACCOUNT,
        status == DriverDocumentStatus.VERIFIED ? NOW : null, reason, NOW, NOW);
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

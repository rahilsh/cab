package in.rsh.cab.driver;

import in.rsh.cab.driver.internal.persistence.DriverDocumentRepository;
import in.rsh.cab.driver.internal.persistence.DriverProfileRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverDocumentService {

  private static final Pattern OBJECT_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,511}$");
  private final DriverDocumentRepository documents;
  private final DriverProfileRepository drivers;
  private final Clock clock;

  public DriverDocumentService(
      DriverDocumentRepository documents, DriverProfileRepository drivers, Clock clock) {
    this.documents = documents;
    this.drivers = drivers;
    this.clock = clock;
  }

  @Transactional
  public DriverDocument submit(
      DriverDocumentType type, String reference, String objectKey, java.time.LocalDate expiresOn) {
    TenantContext context = require(TenantRole.DRIVER);
    DriverProfile driver = drivers.findByTenantIdAndAccountId(context.tenantId(), context.accountId())
        .orElseThrow(() -> new NotFoundException("Driver profile not found"));
    validateObjectKey(objectKey);
    if (reference.contains("://")) {
      throw new InvalidRequestException("Document reference must not be a URL");
    }
    Instant now = clock.instant();
    java.time.LocalDate today = now.atZone(clock.getZone()).toLocalDate();
    if (type == DriverDocumentType.DRIVING_LICENSE && expiresOn == null) {
      throw new InvalidRequestException("Driving license expiry is required");
    }
    if (expiresOn != null && expiresOn.isBefore(today)) {
      throw new InvalidRequestException("Document must not already be expired");
    }
    DriverDocument document = new DriverDocument(
        UUID.randomUUID(), driver.id(), type, reference, objectKey, expiresOn,
        DriverDocumentStatus.PENDING, null, null, null, now, now);
    documents.insert(context.tenantId(), document);
    documents.appendHistory(context.tenantId(), document.id(), null, DriverDocumentStatus.PENDING,
        context.accountId(), null, now);
    return document;
  }

  @Transactional(readOnly = true)
  public List<DriverDocument> listOwn() {
    TenantContext context = require(TenantRole.DRIVER);
    DriverProfile driver = drivers.findByTenantIdAndAccountId(context.tenantId(), context.accountId())
        .orElseThrow(() -> new NotFoundException("Driver profile not found"));
    return documents.findAll(context.tenantId(), driver.id());
  }

  @Transactional(readOnly = true)
  public List<DriverDocument> list(UUID driverId) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    requireDriver(context.tenantId(), driverId);
    return documents.findAll(context.tenantId(), driverId);
  }

  @Transactional
  public DriverDocument verify(UUID driverId, UUID documentId) {
    return review(driverId, documentId, DriverDocumentStatus.VERIFIED, null);
  }

  @Transactional
  public DriverDocument reject(UUID driverId, UUID documentId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new InvalidRequestException("Rejection reason is required");
    }
    return review(driverId, documentId, DriverDocumentStatus.REJECTED, reason);
  }

  private DriverDocument review(
      UUID driverId, UUID documentId, DriverDocumentStatus status, String reason) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    DriverDocument current = documents.find(context.tenantId(), driverId, documentId)
        .orElseThrow(() -> new NotFoundException("Driver document not found"));
    Instant now = clock.instant();
    Instant verifiedAt = status == DriverDocumentStatus.VERIFIED ? now : null;
    if (!documents.review(context.tenantId(), driverId, documentId, status, context.accountId(),
        verifiedAt, reason, now)) {
      throw new ConflictException("Driver document was already reviewed");
    }
    documents.appendHistory(context.tenantId(), documentId, current.verificationStatus(), status,
        context.accountId(), reason, now);
    return documents.find(context.tenantId(), driverId, documentId).orElseThrow();
  }

  private void validateObjectKey(String objectKey) {
    if (!OBJECT_KEY.matcher(objectKey).matches() || objectKey.contains("../")
        || objectKey.contains("/..") || objectKey.contains(":")) {
      throw new InvalidRequestException(
          "Document must use an external object key, not a URL or path traversal");
    }
  }

  private void requireDriver(UUID tenantId, UUID driverId) {
    drivers.findByTenantIdAndId(tenantId, driverId)
        .orElseThrow(() -> new NotFoundException("Driver profile not found"));
  }

  private TenantContext require(TenantRole role) {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(role)) {
      throw new TenantAccessDeniedException(role + " role is required");
    }
    return context;
  }
}

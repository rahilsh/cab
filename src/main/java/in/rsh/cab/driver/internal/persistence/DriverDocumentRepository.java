package in.rsh.cab.driver.internal.persistence;

import in.rsh.cab.driver.DriverDocument;
import in.rsh.cab.driver.DriverDocumentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverDocumentRepository {

  void insert(UUID tenantId, DriverDocument document);

  Optional<DriverDocument> find(UUID tenantId, UUID driverId, UUID documentId);

  List<DriverDocument> findAll(UUID tenantId, UUID driverId);

  boolean review(
      UUID tenantId,
      UUID driverId,
      UUID documentId,
      DriverDocumentStatus status,
      UUID verifierAccountId,
      Instant verifiedAt,
      String rejectionReason,
      Instant updatedAt);

  void appendHistory(
      UUID tenantId,
      UUID documentId,
      DriverDocumentStatus from,
      DriverDocumentStatus to,
      UUID actorAccountId,
      String reason,
      Instant occurredAt);

  boolean hasVerifiedDrivingLicense(UUID tenantId, UUID driverId, LocalDate onDate);
}

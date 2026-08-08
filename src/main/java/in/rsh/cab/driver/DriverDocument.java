package in.rsh.cab.driver;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverDocument(
    UUID id,
    UUID driverId,
    DriverDocumentType documentType,
    String documentReference,
    String objectKey,
    LocalDate expiresOn,
    DriverDocumentStatus verificationStatus,
    UUID verifiedByAccountId,
    Instant verifiedAt,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt) {}

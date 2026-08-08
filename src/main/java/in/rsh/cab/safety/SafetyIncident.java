package in.rsh.cab.safety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SafetyIncident(
    UUID id,
    UUID rideId,
    UUID reportedByAccountId,
    String category,
    String description,
    String state,
    String severity,
    Instant createdAt,
    Instant updatedAt,
    List<Evidence> evidence) {

  public record Evidence(
      UUID id,
      UUID submittedByAccountId,
      String objectKey,
      String mediaType,
      Long sizeBytes,
      String checksumSha256,
      Instant createdAt) {}
}

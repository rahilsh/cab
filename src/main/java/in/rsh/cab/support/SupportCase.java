package in.rsh.cab.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupportCase(
    UUID id,
    UUID openedByAccountId,
    UUID rideId,
    String subject,
    String state,
    String priority,
    Instant createdAt,
    Instant updatedAt,
    long version,
    List<Message> messages) {

  public record Message(
      UUID id, UUID authorAccountId, String body, boolean internal, Instant createdAt) {}
}

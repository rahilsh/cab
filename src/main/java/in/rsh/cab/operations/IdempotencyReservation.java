package in.rsh.cab.operations;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record IdempotencyReservation(
    Status status, UUID recordId, UUID resourceId, int httpStatus, JsonNode safeResponse) {

  public enum Status {
    RESERVED,
    REPLAY
  }

  static IdempotencyReservation reserved(UUID recordId) {
    return new IdempotencyReservation(Status.RESERVED, recordId, null, 0, null);
  }

  static IdempotencyReservation replay(IdempotencyRecord record) {
    return new IdempotencyReservation(
        Status.REPLAY,
        record.id(),
        record.resourceId(),
        record.httpStatus(),
        record.safeResponse());
  }
}

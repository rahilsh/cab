package in.rsh.cab.operations.internal.persistence;

import java.time.Instant;
import java.util.UUID;

public interface InboxRepository {

  boolean insert(UUID id, UUID tenantId, String consumer, UUID eventId, Instant receivedAt);
}

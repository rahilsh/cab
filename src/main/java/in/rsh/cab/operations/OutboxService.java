package in.rsh.cab.operations;

import in.rsh.cab.operations.internal.persistence.OutboxRepository;
import in.rsh.cab.web.RequestMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class OutboxService {

  private final OutboxRepository events;
  private final Clock clock;

  public OutboxService(OutboxRepository events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public UUID append(
      UUID tenantId, String aggregateType, UUID aggregateId, long aggregateVersion,
      String eventType, int eventVersion, JsonNode payload, UUID causationId) {
    UUID id = UUID.randomUUID();
    Instant now = clock.instant();
    events.insert(
        id, tenantId, aggregateType, aggregateId, aggregateVersion, eventType, eventVersion,
        payload, now, now, RequestMetadata.correlationIdOrNull(), causationId);
    return id;
  }

}

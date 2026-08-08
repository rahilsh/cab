package in.rsh.cab.webhook.internal.persistence;

import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.webhook.WebhookSubscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookRepository {

  void insert(UUID tenantId, WebhookSubscription subscription);

  List<WebhookSubscription> findAll(UUID tenantId);

  Optional<WebhookSubscription> find(UUID tenantId, UUID id);

  boolean update(UUID tenantId, WebhookSubscription subscription, long expectedVersion);

  void delete(UUID tenantId, UUID id, Instant now);

  List<WebhookSubscription> matching(UUID tenantId, String eventType);

  Optional<Delivery> createDelivery(WebhookSubscription subscription, UUID tenantId,
      OutboxEvent event, String payload, Instant signatureTimestamp, Instant now);

  List<Delivery> findDue(UUID tenantId, int limit, Instant now);

  void complete(UUID tenantId, UUID deliveryId, int attemptNumber, int responseStatus, Instant now);

  void retry(UUID tenantId, UUID deliveryId, int attemptNumber, Integer responseStatus,
      String errorCode, Instant nextAttemptAt, boolean failed, Instant now);

  record Delivery(
      UUID id, UUID tenantId, WebhookSubscription subscription, UUID eventId, String eventType,
      int eventVersion, String payload, Instant signatureTimestamp, int attemptCount) {}
}

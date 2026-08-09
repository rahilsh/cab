package in.rsh.cab.notification.internal.persistence;

import in.rsh.cab.notification.NotificationPreference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

  NotificationPreference upsertPreference(
      UUID tenantId, UUID recipientId, String eventType, String channel, boolean enabled, Instant now);

  List<NotificationPreference> preferences(UUID tenantId, UUID recipientId);

  boolean preferenceEnabled(UUID tenantId, UUID recipientId, String eventType, String channel);

  Delivery getOrCreateDelivery(UUID id, UUID tenantId, UUID recipientId, UUID eventId, String eventType,
      String channel, String templateKey, int templateVersion, String body, String status, Instant now);

  Optional<Delivery> claimDelivery(
      UUID tenantId, UUID deliveryId, Instant now, Instant leaseExpiresAt, UUID leaseToken);

  List<Delivery> claimDue(
      UUID tenantId, int limit, Instant now, Instant leaseExpiresAt, UUID leaseToken);

  void complete(
      UUID tenantId, UUID deliveryId, UUID leaseToken, int attemptNumber,
      String providerMessageId, Instant now);

  void retry(
      UUID tenantId, UUID deliveryId, UUID leaseToken, int attemptNumber,
      String errorCode, Instant nextAttemptAt, boolean failed, Instant now);

  record Delivery(
      UUID id, UUID tenantId, UUID recipientId, UUID eventId, String eventType, String channel,
      String templateKey, int templateVersion, String body, String status, int attempts,
      UUID leaseToken) {}
}

package in.rsh.cab.notification.internal.persistence;

import in.rsh.cab.notification.NotificationPreference;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {

  NotificationPreference upsertPreference(
      UUID tenantId, UUID recipientId, String eventType, String channel, boolean enabled, Instant now);

  List<NotificationPreference> preferences(UUID tenantId, UUID recipientId);

  boolean preferenceEnabled(UUID tenantId, UUID recipientId, String eventType, String channel);

  Delivery getOrCreateDelivery(UUID id, UUID tenantId, UUID recipientId, UUID eventId, String eventType,
      String channel, String templateKey, int templateVersion, String status, Instant now);

  boolean claimDelivery(UUID tenantId, UUID deliveryId);

  void insertAttempt(UUID tenantId, UUID deliveryId, int number, String status,
      String providerMessageId, String errorCode, Instant now);

  void markDelivery(UUID tenantId, UUID deliveryId, String status, Instant deliveredAt);

  record Delivery(UUID id, String status, int attempts) {}
}

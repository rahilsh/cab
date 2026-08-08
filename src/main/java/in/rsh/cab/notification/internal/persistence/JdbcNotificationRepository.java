package in.rsh.cab.notification.internal.persistence;

import in.rsh.cab.notification.NotificationPreference;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

  private final JdbcClient jdbc;

  public JdbcNotificationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public NotificationPreference upsertPreference(
      UUID tenantId, UUID recipientId, String eventType, String channel, boolean enabled, Instant now) {
    UUID id = UUID.randomUUID();
    return jdbc.sql("""
            INSERT INTO notification_preferences
              (id, tenant_id, recipient_account_id, event_type, channel, enabled, updated_at)
            VALUES (:id, :tenantId, :recipientId, :eventType, :channel, :enabled, :now)
            ON CONFLICT (tenant_id, recipient_account_id, event_type, channel)
            DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
            RETURNING id, event_type, channel, enabled, updated_at
            """)
        .param("id", id).param("tenantId", tenantId).param("recipientId", recipientId)
        .param("eventType", eventType).param("channel", channel).param("enabled", enabled)
        .param("now", Timestamp.from(now)).query((rs, row) -> new NotificationPreference(
            rs.getObject("id", UUID.class), rs.getString("event_type"), rs.getString("channel"),
            rs.getBoolean("enabled"), rs.getTimestamp("updated_at").toInstant())).single();
  }

  @Override
  public List<NotificationPreference> preferences(UUID tenantId, UUID recipientId) {
    return jdbc.sql("""
            SELECT id, event_type, channel, enabled, updated_at FROM notification_preferences
            WHERE tenant_id = :tenantId AND recipient_account_id = :recipientId
            ORDER BY event_type, channel
            """)
        .param("tenantId", tenantId).param("recipientId", recipientId)
        .query((rs, row) -> new NotificationPreference(rs.getObject("id", UUID.class),
            rs.getString("event_type"), rs.getString("channel"), rs.getBoolean("enabled"),
            rs.getTimestamp("updated_at").toInstant())).list();
  }

  @Override
  public boolean preferenceEnabled(
      UUID tenantId, UUID recipientId, String eventType, String channel) {
    return jdbc.sql("""
            SELECT COALESCE((SELECT enabled FROM notification_preferences
              WHERE tenant_id = :tenantId AND recipient_account_id = :recipientId
                AND event_type = :eventType AND channel = :channel), true)
            """)
        .param("tenantId", tenantId).param("recipientId", recipientId)
        .param("eventType", eventType).param("channel", channel).query(Boolean.class).single();
  }

  @Override
  public Delivery getOrCreateDelivery(
      UUID id, UUID tenantId, UUID recipientId, UUID eventId, String eventType, String channel,
      String templateKey, int templateVersion, String status, Instant now) {
    jdbc.sql("""
            INSERT INTO notification_deliveries
              (id, tenant_id, recipient_account_id, event_id, event_type, channel,
               template_key, template_version, status, created_at)
            VALUES (:id, :tenantId, :recipientId, :eventId, :eventType, :channel,
                    :templateKey, :templateVersion, :status, :now)
            ON CONFLICT (tenant_id, recipient_account_id, event_id, channel,
                         template_key, template_version) DO NOTHING
            """)
        .param("id", id).param("tenantId", tenantId).param("recipientId", recipientId)
        .param("eventId", eventId).param("eventType", eventType).param("channel", channel)
        .param("templateKey", templateKey).param("templateVersion", templateVersion)
        .param("status", status).param("now", Timestamp.from(now)).update();
    return jdbc.sql("""
            SELECT d.id, d.status, count(a.id) attempts
            FROM notification_deliveries d LEFT JOIN notification_attempts a
              ON a.tenant_id = d.tenant_id AND a.delivery_id = d.id
            WHERE d.tenant_id = :tenantId AND d.recipient_account_id = :recipientId
              AND d.event_id = :eventId AND d.channel = :channel
              AND d.template_key = :templateKey AND d.template_version = :templateVersion
            GROUP BY d.id, d.status
            """)
        .param("tenantId", tenantId).param("recipientId", recipientId).param("eventId", eventId)
        .param("channel", channel).param("templateKey", templateKey)
        .param("templateVersion", templateVersion)
        .query((rs, row) -> new Delivery(rs.getObject("id", UUID.class), rs.getString("status"),
            rs.getInt("attempts"))).single();
  }

  @Override
  public boolean claimDelivery(UUID tenantId, UUID deliveryId) {
    return jdbc.sql("""
            UPDATE notification_deliveries SET status = 'PROCESSING'
            WHERE tenant_id = :tenantId AND id = :id AND status IN ('PENDING', 'FAILED')
            """)
        .param("tenantId", tenantId).param("id", deliveryId).update() == 1;
  }

  @Override
  public void insertAttempt(UUID tenantId, UUID deliveryId, int number, String status,
      String providerMessageId, String errorCode, Instant now) {
    jdbc.sql("""
            INSERT INTO notification_attempts
              (id, tenant_id, delivery_id, attempt_number, status, provider_message_id,
               error_code, attempted_at)
            VALUES (:id, :tenantId, :deliveryId, :number, :status, :providerId, :error, :now)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId).param("deliveryId", deliveryId)
        .param("number", number).param("status", status).param("providerId", providerMessageId)
        .param("error", errorCode).param("now", Timestamp.from(now)).update();
  }

  @Override
  public void markDelivery(UUID tenantId, UUID deliveryId, String status, Instant deliveredAt) {
    jdbc.sql("""
            UPDATE notification_deliveries SET status = :status, delivered_at = :deliveredAt
            WHERE tenant_id = :tenantId AND id = :id
            """)
        .param("status", status).param("deliveredAt", deliveredAt == null ? null : Timestamp.from(deliveredAt))
        .param("tenantId", tenantId).param("id", deliveryId).update();
  }
}

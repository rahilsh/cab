package in.rsh.cab.notification.internal.persistence;

import in.rsh.cab.notification.NotificationPreference;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
      String templateKey, int templateVersion, String body, String status, Instant now) {
    jdbc.sql("""
            INSERT INTO notification_deliveries
              (id, tenant_id, recipient_account_id, event_id, event_type, channel,
               template_key, template_version, body, status, next_attempt_at, created_at)
             VALUES (:id, :tenantId, :recipientId, :eventId, :eventType, :channel,
                     :templateKey, :templateVersion, :body, :status, :now, :now)
            ON CONFLICT (tenant_id, recipient_account_id, event_id, channel,
                         template_key, template_version) DO NOTHING
            """)
        .param("id", id).param("tenantId", tenantId).param("recipientId", recipientId)
        .param("eventId", eventId).param("eventType", eventType).param("channel", channel)
        .param("templateKey", templateKey).param("templateVersion", templateVersion)
        .param("body", body).param("status", status).param("now", Timestamp.from(now)).update();
    return jdbc.sql("""
            SELECT d.id, d.tenant_id, d.recipient_account_id, d.event_id, d.event_type,
                   d.channel, d.template_key, d.template_version, d.body, d.status,
                   count(a.id) attempts, d.lease_token
            FROM notification_deliveries d LEFT JOIN notification_attempts a
              ON a.tenant_id = d.tenant_id AND a.delivery_id = d.id
            WHERE d.tenant_id = :tenantId AND d.recipient_account_id = :recipientId
              AND d.event_id = :eventId AND d.channel = :channel
              AND d.template_key = :templateKey AND d.template_version = :templateVersion
            GROUP BY d.id
            """)
        .param("tenantId", tenantId).param("recipientId", recipientId).param("eventId", eventId)
        .param("channel", channel).param("templateKey", templateKey)
        .param("templateVersion", templateVersion)
        .query(this::mapDelivery).single();
  }

  @Override
  public Optional<Delivery> claimDelivery(
      UUID tenantId, UUID deliveryId, Instant now, Instant leaseExpiresAt, UUID leaseToken) {
    return claim(tenantId, deliveryId, null, now, leaseExpiresAt, leaseToken).stream().findFirst();
  }

  @Override
  public List<Delivery> claimDue(
      UUID tenantId, int limit, Instant now, Instant leaseExpiresAt, UUID leaseToken) {
    return claim(tenantId, null, limit, now, leaseExpiresAt, leaseToken);
  }

  private void insertAttempt(UUID tenantId, UUID deliveryId, int number, String status,
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
  public void complete(
      UUID tenantId, UUID deliveryId, UUID leaseToken, int attemptNumber,
      String providerMessageId, Instant now) {
    requireClaim(jdbc.sql("""
            UPDATE notification_deliveries
            SET status = 'DELIVERED', delivered_at = :now, lease_token = NULL,
                lease_started_at = NULL, lease_expires_at = NULL
            WHERE tenant_id = :tenantId AND id = :id AND status = 'PROCESSING'
              AND lease_token = :leaseToken
            """)
        .param("now", Timestamp.from(now)).param("tenantId", tenantId).param("id", deliveryId)
        .param("leaseToken", leaseToken).update());
    insertAttempt(tenantId, deliveryId, attemptNumber, "SUCCEEDED", providerMessageId, null, now);
  }

  @Override
  public void retry(
      UUID tenantId, UUID deliveryId, UUID leaseToken, int attemptNumber,
      String errorCode, Instant nextAttemptAt, boolean failed, Instant now) {
    requireClaim(jdbc.sql("""
            UPDATE notification_deliveries
            SET status = :status, next_attempt_at = :nextAttemptAt, lease_token = NULL,
                lease_started_at = NULL, lease_expires_at = NULL
            WHERE tenant_id = :tenantId AND id = :id AND status = 'PROCESSING'
              AND lease_token = :leaseToken
            """)
        .param("status", failed ? "FAILED" : "RETRY")
        .param("nextAttemptAt", Timestamp.from(nextAttemptAt)).param("tenantId", tenantId)
        .param("id", deliveryId).param("leaseToken", leaseToken).update());
    insertAttempt(tenantId, deliveryId, attemptNumber, "FAILED", null, errorCode, now);
  }

  private List<Delivery> claim(
      UUID tenantId, UUID deliveryId, Integer limit, Instant now, Instant leaseExpiresAt,
      UUID leaseToken) {
    return jdbc.sql("""
            WITH candidates AS (
              SELECT id FROM notification_deliveries
              WHERE tenant_id = :tenantId
                AND (:deliveryId IS NULL OR id = :deliveryId)
                AND next_attempt_at <= :now
                AND (status IN ('PENDING', 'RETRY')
                  OR (status = 'PROCESSING' AND lease_expires_at <= :now))
              ORDER BY next_attempt_at, id
              FOR UPDATE SKIP LOCKED
              LIMIT :limit
            )
            UPDATE notification_deliveries delivery
            SET status = 'PROCESSING', lease_token = :leaseToken,
                lease_started_at = :now, lease_expires_at = :leaseExpiresAt
            FROM candidates
            WHERE delivery.tenant_id = :tenantId AND delivery.id = candidates.id
            RETURNING delivery.id, delivery.tenant_id, delivery.recipient_account_id,
              delivery.event_id, delivery.event_type, delivery.channel, delivery.template_key,
              delivery.template_version, delivery.body, delivery.status,
              (SELECT count(*) FROM notification_attempts attempt
                WHERE attempt.tenant_id = delivery.tenant_id
                  AND attempt.delivery_id = delivery.id) attempts,
              delivery.lease_token
            """)
        .param("tenantId", tenantId).param("deliveryId", deliveryId)
        .param("now", Timestamp.from(now)).param("leaseExpiresAt", Timestamp.from(leaseExpiresAt))
        .param("leaseToken", leaseToken).param("limit", limit == null ? 1 : limit)
        .query(this::mapDelivery).list();
  }

  private Delivery mapDelivery(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Delivery(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
        rs.getObject("recipient_account_id", UUID.class), rs.getObject("event_id", UUID.class),
        rs.getString("event_type"), rs.getString("channel"), rs.getString("template_key"),
        rs.getInt("template_version"), rs.getString("body"), rs.getString("status"),
        rs.getInt("attempts"), rs.getObject("lease_token", UUID.class));
  }

  private void requireClaim(int updated) {
    if (updated != 1) {
      throw new IllegalStateException("Notification delivery lease is no longer owned");
    }
  }
}

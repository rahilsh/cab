package in.rsh.cab.webhook.internal.persistence;

import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.webhook.WebhookSubscription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcWebhookRepository implements WebhookRepository {

  private static final String SELECT = """
      SELECT id, url, secret_reference, event_filters, enabled, created_at, updated_at, version
      FROM webhook_subscriptions
      """;
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public JdbcWebhookRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void insert(UUID tenantId, WebhookSubscription subscription) {
    jdbc.sql("""
            INSERT INTO webhook_subscriptions
              (id, tenant_id, url, secret_reference, event_filters, enabled,
               created_at, updated_at, version)
            VALUES (:id, :tenantId, :url, :secret, CAST(:filters AS jsonb), :enabled,
                    :createdAt, :updatedAt, :version)
            """)
        .param("id", subscription.id()).param("tenantId", tenantId).param("url", subscription.url())
        .param("secret", subscription.secretReference()).param("filters", filters(subscription.eventFilters()))
        .param("enabled", subscription.enabled()).param("createdAt", Timestamp.from(subscription.createdAt()))
        .param("updatedAt", Timestamp.from(subscription.updatedAt())).param("version", subscription.version())
        .update();
  }

  @Override
  public List<WebhookSubscription> findAll(UUID tenantId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND deleted_at IS NULL ORDER BY created_at, id")
        .param("tenantId", tenantId).query(this::map).list();
  }

  @Override
  public Optional<WebhookSubscription> find(UUID tenantId, UUID id) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
        .param("tenantId", tenantId).param("id", id).query(this::map).optional();
  }

  @Override
  public boolean update(UUID tenantId, WebhookSubscription subscription, long expectedVersion) {
    return jdbc.sql("""
            UPDATE webhook_subscriptions SET url = :url, secret_reference = :secret,
              event_filters = CAST(:filters AS jsonb), enabled = :enabled,
              updated_at = :updatedAt, version = :version
            WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
              AND deleted_at IS NULL
            """)
        .param("url", subscription.url()).param("secret", subscription.secretReference())
        .param("filters", filters(subscription.eventFilters())).param("enabled", subscription.enabled())
        .param("updatedAt", Timestamp.from(subscription.updatedAt())).param("version", subscription.version())
        .param("tenantId", tenantId).param("id", subscription.id())
        .param("expectedVersion", expectedVersion).update() == 1;
  }

  @Override
  public void delete(UUID tenantId, UUID id, Instant now) {
    jdbc.sql("""
            UPDATE webhook_subscriptions SET enabled = false, deleted_at = :now,
              updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
            """)
        .param("now", Timestamp.from(now)).param("tenantId", tenantId).param("id", id).update();
  }

  @Override
  public List<WebhookSubscription> matching(UUID tenantId, String eventType) {
    return jdbc.sql(SELECT + """
            WHERE tenant_id = :tenantId AND enabled AND deleted_at IS NULL
              AND event_filters ? :eventType
            ORDER BY id
            """)
        .param("tenantId", tenantId).param("eventType", eventType).query(this::map).list();
  }

  @Override
  public Optional<Delivery> createDelivery(
      WebhookSubscription subscription, UUID tenantId, OutboxEvent event, String payload,
      Instant signatureTimestamp, Instant now) {
    UUID id = UUID.randomUUID();
    int inserted = jdbc.sql("""
            INSERT INTO webhook_deliveries
              (id, tenant_id, subscription_id, event_id, event_type, event_version, payload,
               signature_timestamp, status, attempt_count, next_attempt_at, created_at)
            VALUES (:id, :tenantId, :subscriptionId, :eventId, :eventType, :eventVersion,
                    CAST(:payload AS jsonb), :signatureTimestamp, 'PENDING', 0, :now, :now)
            ON CONFLICT (tenant_id, subscription_id, event_id) DO NOTHING
            """)
        .param("id", id).param("tenantId", tenantId).param("subscriptionId", subscription.id())
        .param("eventId", event.id()).param("eventType", event.eventType())
        .param("eventVersion", event.eventVersion()).param("payload", payload)
        .param("signatureTimestamp", Timestamp.from(signatureTimestamp))
        .param("now", Timestamp.from(now)).update();
    return inserted == 0 ? Optional.empty() : Optional.of(new Delivery(id, tenantId, subscription,
        event.id(), event.eventType(), event.eventVersion(), payload, signatureTimestamp, 0, null));
  }

  @Override
  public List<Delivery> claimDue(
      UUID tenantId, int limit, Instant now, Instant leaseExpiresAt, UUID leaseToken) {
    return jdbc.sql("""
            WITH candidates AS (
              SELECT d.id
              FROM webhook_deliveries d JOIN webhook_subscriptions s
                ON s.tenant_id = d.tenant_id AND s.id = d.subscription_id
              WHERE d.tenant_id = :tenantId AND d.next_attempt_at <= :now
                AND (d.status IN ('PENDING', 'RETRY')
                  OR (d.status = 'PROCESSING' AND d.lease_expires_at <= :now))
                AND s.enabled AND s.deleted_at IS NULL
              ORDER BY d.next_attempt_at, d.id
              FOR UPDATE OF d SKIP LOCKED
              LIMIT :limit
            ), claimed AS (
              UPDATE webhook_deliveries d
              SET status = 'PROCESSING', lease_token = :leaseToken,
                  lease_started_at = :now, lease_expires_at = :leaseExpiresAt,
                  signature_timestamp = :now
              FROM candidates
              WHERE d.tenant_id = :tenantId AND d.id = candidates.id
              RETURNING d.*
            )
            SELECT d.id delivery_id, d.tenant_id, d.event_id, d.event_type, d.event_version,
                   d.payload, d.signature_timestamp, d.attempt_count, d.lease_token,
                   s.id, s.url, s.secret_reference, s.event_filters, s.enabled,
                   s.created_at, s.updated_at, s.version
            FROM claimed d JOIN webhook_subscriptions s
              ON s.tenant_id = d.tenant_id AND s.id = d.subscription_id
            ORDER BY d.next_attempt_at, d.id
            """)
        .param("tenantId", tenantId).param("now", Timestamp.from(now))
        .param("leaseExpiresAt", Timestamp.from(leaseExpiresAt)).param("leaseToken", leaseToken)
        .param("limit", limit)
        .query((rs, row) -> new Delivery(rs.getObject("delivery_id", UUID.class),
            rs.getObject("tenant_id", UUID.class), map(rs, row),
            rs.getObject("event_id", UUID.class), rs.getString("event_type"),
            rs.getInt("event_version"), rs.getString("payload"),
            rs.getTimestamp("signature_timestamp").toInstant(), rs.getInt("attempt_count"),
            rs.getObject("lease_token", UUID.class)))
        .list();
  }

  @Override
  public void complete(
      UUID tenantId, UUID deliveryId, UUID leaseToken, int attemptNumber,
      int responseStatus, Instant now) {
    requireClaim(jdbc.sql("""
             UPDATE webhook_deliveries SET status = 'DELIVERED', attempt_count = :attempt,
               delivered_at = :now, lease_token = NULL, lease_started_at = NULL,
               lease_expires_at = NULL
             WHERE tenant_id = :tenantId AND id = :id AND status = 'PROCESSING'
               AND lease_token = :leaseToken
             """)
        .param("attempt", attemptNumber).param("now", Timestamp.from(now))
        .param("tenantId", tenantId).param("id", deliveryId).param("leaseToken", leaseToken)
        .update());
    attempt(tenantId, deliveryId, attemptNumber, "SUCCEEDED", responseStatus, null, now);
  }

  @Override
  public void retry(
      UUID tenantId, UUID deliveryId, UUID leaseToken, int attemptNumber, Integer responseStatus,
      String errorCode, Instant nextAttemptAt, boolean failed, Instant now) {
    requireClaim(jdbc.sql("""
             UPDATE webhook_deliveries SET status = :status, attempt_count = :attempt,
               next_attempt_at = :nextAttempt, lease_token = NULL, lease_started_at = NULL,
               lease_expires_at = NULL
             WHERE tenant_id = :tenantId AND id = :id AND status = 'PROCESSING'
               AND lease_token = :leaseToken
             """)
        .param("status", failed ? "FAILED" : "RETRY").param("attempt", attemptNumber)
        .param("nextAttempt", Timestamp.from(nextAttemptAt)).param("tenantId", tenantId)
        .param("id", deliveryId).param("leaseToken", leaseToken).update());
    attempt(tenantId, deliveryId, attemptNumber, "FAILED", responseStatus, errorCode, now);
  }

  private void attempt(UUID tenantId, UUID deliveryId, int number, String status,
      Integer responseStatus, String error, Instant now) {
    jdbc.sql("""
            INSERT INTO webhook_attempts
              (id, tenant_id, delivery_id, attempt_number, status, response_status,
               error_code, attempted_at)
            VALUES (:id, :tenantId, :deliveryId, :number, :status, :responseStatus, :error, :now)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId).param("deliveryId", deliveryId)
        .param("number", number).param("status", status).param("responseStatus", responseStatus)
        .param("error", error).param("now", Timestamp.from(now)).update();
  }

  private WebhookSubscription map(ResultSet rs, int row) throws SQLException {
    try {
      String[] values = json.readValue(rs.getString("event_filters"), String[].class);
      return new WebhookSubscription(rs.getObject("id", UUID.class), rs.getString("url"),
          rs.getString("secret_reference"), new LinkedHashSet<>(Arrays.asList(values)),
          rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant(),
          rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    } catch (JacksonException exception) {
      throw new SQLException("Stored webhook filters are invalid", exception);
    }
  }

  private String filters(Set<String> values) {
    try {
      return json.writeValueAsString(values);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Webhook filters are invalid", exception);
    }
  }

  private void requireClaim(int updated) {
    if (updated != 1) {
      throw new IllegalStateException("Webhook delivery lease is no longer owned");
    }
  }
}

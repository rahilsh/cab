package in.rsh.cab.operations.internal.persistence;

import in.rsh.cab.operations.OutboxEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public JdbcOutboxRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public void insert(
      UUID id, UUID tenantId, String aggregateType, UUID aggregateId, long aggregateVersion,
      String eventType, int eventVersion, JsonNode payload, Instant occurredAt, Instant availableAt,
      String correlationId, UUID causationId) {
    jdbc.sql("""
            INSERT INTO outbox_events
              (id, tenant_id, aggregate_type, aggregate_id, aggregate_version, event_type,
               event_version, payload, occurred_at, available_at, correlation_id, causation_id,
               status, attempts)
            VALUES
              (:id, :tenantId, :aggregateType, :aggregateId, :aggregateVersion, :eventType,
               :eventVersion, CAST(:payload AS jsonb), :occurredAt, :availableAt, :correlationId,
               :causationId, 'PENDING', 0)
            """)
        .param("id", id).param("tenantId", tenantId).param("aggregateType", aggregateType)
        .param("aggregateId", aggregateId).param("aggregateVersion", aggregateVersion)
        .param("eventType", eventType).param("eventVersion", eventVersion)
        .param("payload", payload.toString()).param("occurredAt", Timestamp.from(occurredAt))
        .param("availableAt", Timestamp.from(availableAt)).param("correlationId", correlationId)
        .param("causationId", causationId).update();
  }

  @Override
  public List<OutboxEvent> lease(UUID tenantId, int limit, Instant now, Instant leaseExpiresAt) {
    return jdbc.sql("""
            WITH candidates AS (
              SELECT id
              FROM outbox_events
              WHERE tenant_id = :tenantId AND available_at <= :now
                AND (status = 'PENDING'
                  OR (status = 'PROCESSING' AND lease_expires_at <= :now))
              ORDER BY available_at, occurred_at, id
              FOR UPDATE SKIP LOCKED
              LIMIT :limit
            )
            UPDATE outbox_events event
            SET status = 'PROCESSING', attempts = event.attempts + 1,
                lease_started_at = :now, lease_expires_at = :leaseExpiresAt,
                published_at = NULL, last_error = NULL
            FROM candidates
            WHERE event.tenant_id = :tenantId AND event.id = candidates.id
            RETURNING event.id, event.tenant_id, event.aggregate_type, event.aggregate_id,
                      event.aggregate_version, event.event_type, event.event_version, event.payload,
                      event.occurred_at, event.correlation_id, event.causation_id, event.attempts
            """)
        .param("tenantId", tenantId).param("now", Timestamp.from(now))
        .param("leaseExpiresAt", Timestamp.from(leaseExpiresAt))
        .param("limit", limit).query(this::map).list();
  }

  @Override
  public void markPublished(UUID tenantId, UUID eventId, Instant publishedAt) {
    requireOne(jdbc.sql("""
            UPDATE outbox_events
            SET status = 'PUBLISHED', published_at = :publishedAt,
                lease_started_at = NULL, lease_expires_at = NULL, last_error = NULL
            WHERE tenant_id = :tenantId AND id = :eventId AND status = 'PROCESSING'
            """)
        .param("publishedAt", Timestamp.from(publishedAt)).param("tenantId", tenantId)
        .param("eventId", eventId).update());
  }

  @Override
  public void markRetry(UUID tenantId, UUID eventId, Instant availableAt, String error) {
    requireOne(jdbc.sql("""
            UPDATE outbox_events
            SET status = 'PENDING', available_at = :availableAt,
                lease_started_at = NULL, lease_expires_at = NULL, last_error = :error
            WHERE tenant_id = :tenantId AND id = :eventId AND status = 'PROCESSING'
            """)
        .param("availableAt", Timestamp.from(availableAt)).param("error", error)
        .param("tenantId", tenantId).param("eventId", eventId).update());
  }

  @Override
  public void markFailed(UUID tenantId, UUID eventId, String error) {
    requireOne(jdbc.sql("""
            UPDATE outbox_events
            SET status = 'FAILED', lease_started_at = NULL, lease_expires_at = NULL,
                last_error = :error
            WHERE tenant_id = :tenantId AND id = :eventId AND status = 'PROCESSING'
            """)
        .param("error", error).param("tenantId", tenantId).param("eventId", eventId).update());
  }

  private OutboxEvent map(ResultSet resultSet, int rowNumber) throws SQLException {
    try {
      return new OutboxEvent(
          resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
          resultSet.getString("aggregate_type"), resultSet.getObject("aggregate_id", UUID.class),
          resultSet.getLong("aggregate_version"), resultSet.getString("event_type"),
          resultSet.getInt("event_version"), objectMapper.readTree(resultSet.getString("payload")),
          resultSet.getTimestamp("occurred_at").toInstant(), resultSet.getString("correlation_id"),
          resultSet.getObject("causation_id", UUID.class), resultSet.getInt("attempts"));
    } catch (JacksonException exception) {
      throw new SQLException("Stored outbox payload is invalid", exception);
    }
  }

  private void requireOne(int updated) {
    if (updated != 1) {
      throw new IllegalStateException("Outbox event is not leased by this publisher");
    }
  }
}

package in.rsh.cab.operations.internal.persistence;

import in.rsh.cab.operations.IdempotencyRecord;
import in.rsh.cab.operations.IdempotencyState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public JdbcIdempotencyRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean insertReservation(
      UUID id, UUID tenantId, UUID actorAccountId, String operation, String key,
      String requestHash, Instant now, Instant expiresAt) {
    return jdbc.sql("""
            INSERT INTO idempotency_records
              (id, tenant_id, actor_account_id, operation, idempotency_key, request_hash,
               state, created_at, expires_at, updated_at)
            VALUES
              (:id, :tenantId, :actorId, :operation, :key, :hash,
               'IN_PROGRESS', :now, :expiresAt, :now)
            ON CONFLICT (tenant_id, actor_account_id, operation, idempotency_key) DO NOTHING
            """)
        .param("id", id).param("tenantId", tenantId).param("actorId", actorAccountId)
        .param("operation", operation).param("key", key).param("hash", requestHash)
        .param("now", Timestamp.from(now)).param("expiresAt", Timestamp.from(expiresAt))
        .update() == 1;
  }

  @Override
  public Optional<IdempotencyRecord> lock(
      UUID tenantId, UUID actorAccountId, String operation, String key) {
    return jdbc.sql("""
            SELECT id, tenant_id, actor_account_id, operation, idempotency_key, request_hash,
                   state, resource_id, http_status, safe_response, expires_at
            FROM idempotency_records
            WHERE tenant_id = :tenantId AND actor_account_id = :actorId
              AND operation = :operation AND idempotency_key = :key
            FOR UPDATE
            """)
        .param("tenantId", tenantId).param("actorId", actorAccountId)
        .param("operation", operation).param("key", key).query(this::map).optional();
  }

  @Override
  public void replaceReservation(
      UUID tenantId, UUID actorAccountId, UUID oldId, UUID newId,
      String requestHash, Instant now, Instant expiresAt) {
    int updated = jdbc.sql("""
            UPDATE idempotency_records
            SET id = :newId, request_hash = :hash, state = 'IN_PROGRESS',
                resource_type = NULL, resource_id = NULL, http_status = NULL,
                safe_response = NULL, created_at = :now, expires_at = :expiresAt, updated_at = :now
            WHERE tenant_id = :tenantId AND actor_account_id = :actorId AND id = :oldId
            """)
        .param("newId", newId).param("hash", requestHash).param("now", Timestamp.from(now))
        .param("expiresAt", Timestamp.from(expiresAt)).param("tenantId", tenantId)
        .param("actorId", actorAccountId).param("oldId", oldId).update();
    requireOne(updated);
  }

  @Override
  public void complete(
      UUID tenantId, UUID actorAccountId, UUID recordId, String resourceType, UUID resourceId,
      int httpStatus, JsonNode safeResponse, Instant now) {
    int updated = jdbc.sql("""
            UPDATE idempotency_records
            SET state = 'COMPLETED', resource_type = :resourceType, resource_id = :resourceId,
                http_status = :httpStatus, safe_response = CAST(:response AS jsonb), updated_at = :now
            WHERE tenant_id = :tenantId AND actor_account_id = :actorId
              AND id = :recordId AND state = 'IN_PROGRESS'
            """)
        .param("resourceType", resourceType).param("resourceId", resourceId)
        .param("httpStatus", httpStatus)
        .param("response", safeResponse == null ? null : safeResponse.toString())
        .param("now", Timestamp.from(now)).param("tenantId", tenantId)
        .param("actorId", actorAccountId).param("recordId", recordId).update();
    requireOne(updated);
  }

  @Override
  public void fail(UUID tenantId, UUID actorAccountId, UUID recordId, Instant now) {
    int updated = jdbc.sql("""
            UPDATE idempotency_records
            SET state = 'FAILED', updated_at = :now
            WHERE tenant_id = :tenantId AND actor_account_id = :actorId
              AND id = :recordId AND state = 'IN_PROGRESS'
            """)
        .param("now", Timestamp.from(now)).param("tenantId", tenantId)
        .param("actorId", actorAccountId).param("recordId", recordId).update();
    requireOne(updated);
  }

  private IdempotencyRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
    try {
      String response = resultSet.getString("safe_response");
      Integer httpStatus = (Integer) resultSet.getObject("http_status");
      return new IdempotencyRecord(
          resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
          resultSet.getObject("actor_account_id", UUID.class), resultSet.getString("operation"),
          resultSet.getString("idempotency_key"), resultSet.getString("request_hash"),
          IdempotencyState.valueOf(resultSet.getString("state")),
          resultSet.getObject("resource_id", UUID.class), httpStatus == null ? 0 : httpStatus,
          response == null ? null : objectMapper.readTree(response),
          resultSet.getTimestamp("expires_at").toInstant());
    } catch (JacksonException exception) {
      throw new SQLException("Stored idempotency response is invalid", exception);
    }
  }

  private void requireOne(int updated) {
    if (updated != 1) {
      throw new IllegalStateException("Idempotency record state changed unexpectedly");
    }
  }
}

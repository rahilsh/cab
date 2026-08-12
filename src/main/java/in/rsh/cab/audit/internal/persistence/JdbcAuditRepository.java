package in.rsh.cab.audit.internal.persistence;

import in.rsh.cab.audit.AuditEvent;
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
public class JdbcAuditRepository implements AuditRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public JdbcAuditRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public void insert(
      UUID id, UUID tenantId, UUID actorAccountId, String action, String targetType, UUID targetId,
      String outcome, String correlationId, JsonNode summary, Instant occurredAt) {
    jdbc.sql("""
            INSERT INTO audit_events
              (id, tenant_id, actor_account_id, action, target_type, target_id,
               outcome, correlation_id, summary, occurred_at)
            VALUES
              (:id, :tenantId, :actorId, :action, :targetType, :targetId,
               :outcome, :correlationId, CAST(:summary AS jsonb), :occurredAt)
            """)
        .param("id", id).param("tenantId", tenantId).param("actorId", actorAccountId)
        .param("action", action).param("targetType", targetType).param("targetId", targetId)
        .param("outcome", outcome).param("correlationId", correlationId)
        .param("summary", summary.toString()).param("occurredAt", Timestamp.from(occurredAt)).update();
  }

  @Override
  public List<AuditEvent> findLatest(UUID tenantId, int limit) {
    return jdbc.sql("""
            SELECT id, actor_account_id, action, target_type, target_id, outcome,
                   correlation_id, summary, occurred_at
            FROM audit_events
            WHERE tenant_id = :tenantId
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit
            """)
        .param("tenantId", tenantId).param("limit", limit).query(this::map).list();
  }

  private AuditEvent map(ResultSet resultSet, int rowNumber) throws SQLException {
    try {
      return new AuditEvent(
          resultSet.getObject("id", UUID.class),
          resultSet.getObject("actor_account_id", UUID.class), resultSet.getString("action"),
          resultSet.getString("target_type"), resultSet.getObject("target_id", UUID.class),
          resultSet.getString("outcome"), resultSet.getString("correlation_id"),
          objectMapper.readTree(resultSet.getString("summary")),
          resultSet.getTimestamp("occurred_at").toInstant());
    } catch (JacksonException exception) {
      throw new SQLException("Stored audit summary is invalid", exception);
    }
  }
}

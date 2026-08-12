package in.rsh.cab.safety.internal.persistence;

import in.rsh.cab.safety.SafetyIncident;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSafetyRepository implements SafetyRepository {

  private static final String SELECT = """
      SELECT id, ride_id, reported_by_account_id, category, description, state, severity,
             created_at, updated_at, version FROM safety_incidents
      """;
  private final JdbcClient jdbc;

  public JdbcSafetyRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isRideParticipant(UUID tenantId, UUID rideId, UUID accountId) {
    return jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM rides r LEFT JOIN driver_profiles d
              ON d.tenant_id = r.tenant_id AND d.id = r.driver_id
              WHERE r.tenant_id = :tenantId AND r.id = :rideId
                AND (r.rider_account_id = :accountId OR d.account_id = :accountId))
            """)
        .param("tenantId", tenantId).param("rideId", rideId).param("accountId", accountId)
        .query(Boolean.class).single();
  }

  @Override
  public void insert(UUID tenantId, SafetyIncident incident) {
    jdbc.sql("""
            INSERT INTO safety_incidents
              (id, tenant_id, ride_id, reported_by_account_id, category, description,
               state, severity, created_at, updated_at)
            VALUES (:id, :tenantId, :rideId, :reporter, :category, :description,
                    :state, :severity, :now, :now)
            """)
        .param("id", incident.id()).param("tenantId", tenantId).param("rideId", incident.rideId())
        .param("reporter", incident.reportedByAccountId()).param("category", incident.category())
        .param("description", incident.description()).param("state", incident.state())
        .param("severity", incident.severity()).param("now", Timestamp.from(incident.createdAt()))
        .update();
  }

  @Override
  public Optional<SafetyIncident> find(UUID tenantId, UUID incidentId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id = :id")
        .param("tenantId", tenantId).param("id", incidentId).query(this::map).optional()
        .map(incident -> withEvidence(tenantId, incident));
  }

  @Override
  public List<SafetyIncident> findAll(UUID tenantId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId ORDER BY created_at DESC, id")
        .param("tenantId", tenantId).query(this::map).list().stream()
        .map(incident -> withEvidence(tenantId, incident)).toList();
  }

  @Override
  public void insertEvidence(UUID tenantId, UUID incidentId, SafetyIncident.Evidence evidence) {
    jdbc.sql("""
            INSERT INTO safety_evidence
              (id, tenant_id, incident_id, submitted_by_account_id, object_key,
               media_type, size_bytes, checksum_sha256, created_at)
            VALUES (:id, :tenantId, :incidentId, :submitter, :objectKey,
                    :mediaType, :sizeBytes, :checksum, :now)
            """)
        .param("id", evidence.id()).param("tenantId", tenantId).param("incidentId", incidentId)
        .param("submitter", evidence.submittedByAccountId()).param("objectKey", evidence.objectKey())
        .param("mediaType", evidence.mediaType()).param("sizeBytes", evidence.sizeBytes())
        .param("checksum", evidence.checksumSha256()).param("now", Timestamp.from(evidence.createdAt()))
        .update();
  }

  @Override
  public boolean update(
      UUID tenantId, UUID incidentId, String expectedState, String state, String severity,
      long expectedVersion, Instant now) {
    return jdbc.sql("""
            UPDATE safety_incidents
            SET state = :state, severity = :severity, updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND id = :id AND state = :expectedState
              AND version = :expectedVersion
            """)
        .param("state", state).param("severity", severity).param("now", Timestamp.from(now))
        .param("tenantId", tenantId).param("id", incidentId).param("expectedState", expectedState)
        .param("expectedVersion", expectedVersion).update() == 1;
  }

  @Override
  public void appendAction(
      UUID tenantId, UUID incidentId, UUID actorId, String action, String fromState,
      String toState, String note, Instant now) {
    jdbc.sql("""
            INSERT INTO safety_incident_actions
              (id, tenant_id, incident_id, actor_account_id, action, from_state,
               to_state, redacted_note, occurred_at)
            VALUES (:id, :tenantId, :incidentId, :actorId, :action, :fromState,
                    :toState, :note, :now)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId)
        .param("incidentId", incidentId).param("actorId", actorId).param("action", action)
        .param("fromState", fromState).param("toState", toState).param("note", note)
        .param("now", Timestamp.from(now)).update();
  }

  private SafetyIncident map(ResultSet rs, int row) throws SQLException {
    return new SafetyIncident(rs.getObject("id", UUID.class), rs.getObject("ride_id", UUID.class),
        rs.getObject("reported_by_account_id", UUID.class), rs.getString("category"),
        rs.getString("description"), rs.getString("state"), rs.getString("severity"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
        rs.getLong("version"), List.of());
  }

  private SafetyIncident withEvidence(UUID tenantId, SafetyIncident incident) {
    List<SafetyIncident.Evidence> evidence = jdbc.sql("""
            SELECT id, submitted_by_account_id, object_key, media_type, size_bytes,
                   checksum_sha256, created_at
            FROM safety_evidence
            WHERE tenant_id = :tenantId AND incident_id = :incidentId
            ORDER BY created_at, id
            """)
        .param("tenantId", tenantId).param("incidentId", incident.id())
        .query((rs, row) -> new SafetyIncident.Evidence(rs.getObject("id", UUID.class),
            rs.getObject("submitted_by_account_id", UUID.class), rs.getString("object_key"),
            rs.getString("media_type"), rs.getObject("size_bytes", Long.class),
            rs.getString("checksum_sha256"), rs.getTimestamp("created_at").toInstant()))
        .list();
    return new SafetyIncident(incident.id(), incident.rideId(), incident.reportedByAccountId(),
        incident.category(), incident.description(), incident.state(), incident.severity(),
        incident.createdAt(), incident.updatedAt(), incident.version(), evidence);
  }
}

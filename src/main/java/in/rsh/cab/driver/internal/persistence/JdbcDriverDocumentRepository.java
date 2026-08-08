package in.rsh.cab.driver.internal.persistence;

import in.rsh.cab.driver.DriverDocument;
import in.rsh.cab.driver.DriverDocumentStatus;
import in.rsh.cab.driver.DriverDocumentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDriverDocumentRepository implements DriverDocumentRepository {

  private static final String SELECT = """
      SELECT id, driver_id, document_type, document_reference, object_key, expires_on,
             verification_status, verified_by_account_id, verified_at, rejection_reason,
             created_at, updated_at
      FROM driver_documents
      """;
  private final JdbcClient jdbc;

  public JdbcDriverDocumentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(UUID tenantId, DriverDocument document) {
    jdbc.sql("""
            INSERT INTO driver_documents
              (id, tenant_id, driver_id, document_type, document_reference, object_key,
               expires_on, verification_status, created_at, updated_at)
            VALUES (:id, :tenantId, :driverId, :type, :reference, :objectKey,
                    :expiresOn, :status, :createdAt, :updatedAt)
            """)
        .param("id", document.id()).param("tenantId", tenantId)
        .param("driverId", document.driverId()).param("type", document.documentType().name())
        .param("reference", document.documentReference()).param("objectKey", document.objectKey())
        .param("expiresOn", document.expiresOn()).param("status", document.verificationStatus().name())
        .param("createdAt", Timestamp.from(document.createdAt()))
        .param("updatedAt", Timestamp.from(document.updatedAt())).update();
  }

  @Override
  public Optional<DriverDocument> find(UUID tenantId, UUID driverId, UUID documentId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND driver_id = :driverId AND id = :id")
        .param("tenantId", tenantId).param("driverId", driverId).param("id", documentId)
        .query(this::map).optional();
  }

  @Override
  public List<DriverDocument> findAll(UUID tenantId, UUID driverId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND driver_id = :driverId ORDER BY created_at, id")
        .param("tenantId", tenantId).param("driverId", driverId).query(this::map).list();
  }

  @Override
  public boolean review(
      UUID tenantId, UUID driverId, UUID documentId, DriverDocumentStatus status,
      UUID verifierAccountId, Instant verifiedAt, String rejectionReason, Instant updatedAt) {
    return jdbc.sql("""
            UPDATE driver_documents
            SET verification_status = :status, verified_by_account_id = :verifier,
                verified_at = :verifiedAt, rejection_reason = :reason, updated_at = :updatedAt
            WHERE tenant_id = :tenantId AND driver_id = :driverId AND id = :id
              AND verification_status = 'PENDING'
            """)
        .param("tenantId", tenantId).param("driverId", driverId).param("id", documentId)
        .param("status", status.name()).param("verifier", verifierAccountId)
        .param("verifiedAt", verifiedAt == null ? null : Timestamp.from(verifiedAt))
        .param("reason", rejectionReason).param("updatedAt", Timestamp.from(updatedAt)).update() == 1;
  }

  @Override
  public void appendHistory(
      UUID tenantId, UUID documentId, DriverDocumentStatus from, DriverDocumentStatus to,
      UUID actorAccountId, String reason, Instant occurredAt) {
    jdbc.sql("""
            INSERT INTO driver_document_verification_history
              (id, tenant_id, document_id, from_status, to_status, actor_account_id, reason, occurred_at)
            VALUES (:id, :tenantId, :documentId, :fromStatus, :toStatus, :actorId, :reason, :occurredAt)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId)
        .param("documentId", documentId).param("fromStatus", from == null ? null : from.name())
        .param("toStatus", to.name()).param("actorId", actorAccountId).param("reason", reason)
        .param("occurredAt", Timestamp.from(occurredAt)).update();
  }

  @Override
  public boolean hasVerifiedDrivingLicense(UUID tenantId, UUID driverId, LocalDate onDate) {
    return jdbc.sql("""
            SELECT EXISTS (
              SELECT 1 FROM driver_documents
              WHERE tenant_id = :tenantId AND driver_id = :driverId
                AND document_type = 'DRIVING_LICENSE' AND verification_status = 'VERIFIED'
                AND (expires_on IS NULL OR expires_on >= :onDate))
            """)
        .param("tenantId", tenantId).param("driverId", driverId).param("onDate", onDate)
        .query(Boolean.class).single();
  }

  private DriverDocument map(ResultSet rs, int rowNumber) throws SQLException {
    Timestamp verifiedAt = rs.getTimestamp("verified_at");
    return new DriverDocument(
        rs.getObject("id", UUID.class), rs.getObject("driver_id", UUID.class),
        DriverDocumentType.valueOf(rs.getString("document_type")),
        rs.getString("document_reference"), rs.getString("object_key"),
        rs.getObject("expires_on", LocalDate.class),
        DriverDocumentStatus.valueOf(rs.getString("verification_status")),
        rs.getObject("verified_by_account_id", UUID.class),
        verifiedAt == null ? null : verifiedAt.toInstant(), rs.getString("rejection_reason"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
  }
}

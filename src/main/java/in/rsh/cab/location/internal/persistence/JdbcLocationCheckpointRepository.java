package in.rsh.cab.location.internal.persistence;

import in.rsh.cab.location.DriverLocation;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLocationCheckpointRepository implements LocationCheckpointRepository {

  private final JdbcClient jdbc;

  public JdbcLocationCheckpointRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfNewer(UUID tenantId, DriverLocation location, Instant createdAt) {
    return jdbc.sql("""
            WITH shift_lock AS (
              SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))
            )
            INSERT INTO driver_location_checkpoints
              (id, tenant_id, shift_id, point, recorded_at, sequence, created_at)
            SELECT :id, :tenantId, :shiftId,
              ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
              :recordedAt, :sequence, :createdAt
            FROM shift_lock
            WHERE NOT EXISTS (
              SELECT 1 FROM driver_location_checkpoints
              WHERE tenant_id = :tenantId AND shift_id = :shiftId
                AND (sequence >= :sequence OR recorded_at >= :recordedAt)
            )
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId)
        .param("lockKey", tenantId + ":" + location.shiftId())
        .param("shiftId", location.shiftId()).param("longitude", location.point().longitude())
        .param("latitude", location.point().latitude())
        .param("recordedAt", Timestamp.from(location.recordedAt()))
        .param("sequence", location.sequence()).param("createdAt", Timestamp.from(createdAt))
        .update() == 1;
  }

  @Override
  public List<DriverLocation> findLatestEligibleAfter(
      UUID tenantId, Instant recordedSince, LocalDate currentDate, UUID afterShiftId, int limit) {
    return jdbc.sql("""
            SELECT DISTINCT ON (c.shift_id) c.shift_id,
              ST_Y(c.point) AS latitude, ST_X(c.point) AS longitude,
              c.recorded_at, c.sequence
            FROM driver_location_checkpoints c
            JOIN driver_shifts s ON s.tenant_id = c.tenant_id AND s.id = c.shift_id
            JOIN driver_profiles d ON d.tenant_id = s.tenant_id AND d.id = s.driver_id
            JOIN vehicles v ON v.tenant_id = s.tenant_id AND v.id = s.vehicle_id
            WHERE c.tenant_id = :tenantId AND c.recorded_at >= :recordedSince
              AND (:afterShiftId IS NULL OR c.shift_id > CAST(:afterShiftId AS uuid))
              AND s.status = 'AVAILABLE' AND d.status = 'APPROVED' AND v.status = 'ACTIVE'
              AND EXISTS (
                SELECT 1 FROM driver_documents document
                WHERE document.tenant_id = d.tenant_id AND document.driver_id = d.id
                  AND document.document_type = 'DRIVING_LICENSE'
                  AND document.verification_status = 'VERIFIED'
                  AND document.expires_on >= :currentDate
              )
            ORDER BY c.shift_id, c.sequence DESC, c.recorded_at DESC
            LIMIT :limit
            """)
        .param("tenantId", tenantId).param("recordedSince", Timestamp.from(recordedSince))
        .param("afterShiftId", afterShiftId)
        .param("currentDate", currentDate).param("limit", Math.max(1, Math.min(limit, 500)))
        .query((rs, row) -> new DriverLocation(rs.getObject("shift_id", UUID.class),
            new in.rsh.cab.geography.GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude")),
            rs.getTimestamp("recorded_at").toInstant(), rs.getLong("sequence")))
        .list();
  }

  @Override
  public int deleteCreatedBefore(UUID tenantId, Instant cutoff, int limit) {
    return jdbc.sql("""
            DELETE FROM driver_location_checkpoints checkpoint
            USING (
              SELECT id FROM driver_location_checkpoints
              WHERE tenant_id = :tenantId AND created_at < :cutoff
              ORDER BY created_at, id
              LIMIT :limit
            ) expired
            WHERE checkpoint.tenant_id = :tenantId AND checkpoint.id = expired.id
            """)
        .param("tenantId", tenantId).param("cutoff", Timestamp.from(cutoff))
        .param("limit", Math.max(1, Math.min(limit, 10_000))).update();
  }
}

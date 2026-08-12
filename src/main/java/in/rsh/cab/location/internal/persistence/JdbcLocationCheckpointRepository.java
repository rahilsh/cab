package in.rsh.cab.location.internal.persistence;

import in.rsh.cab.location.DriverLocation;
import java.sql.Timestamp;
import java.time.Instant;
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
  public void insert(UUID tenantId, DriverLocation location, Instant createdAt) {
    jdbc.sql("""
            INSERT INTO driver_location_checkpoints
              (id, tenant_id, shift_id, point, recorded_at, sequence, created_at)
            VALUES (:id, :tenantId, :shiftId,
              ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
              :recordedAt, :sequence, :createdAt)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId)
        .param("shiftId", location.shiftId()).param("longitude", location.point().longitude())
        .param("latitude", location.point().latitude())
        .param("recordedAt", Timestamp.from(location.recordedAt()))
        .param("sequence", location.sequence()).param("createdAt", Timestamp.from(createdAt)).update();
  }
}

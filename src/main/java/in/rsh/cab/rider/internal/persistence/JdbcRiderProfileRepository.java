package in.rsh.cab.rider.internal.persistence;

import in.rsh.cab.rider.RiderProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRiderProfileRepository implements RiderProfileRepository {

  private final JdbcClient jdbc;

  public JdbcRiderProfileRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<RiderProfile> findByTenantIdAndAccountId(UUID tenantId, UUID accountId) {
    return jdbc.sql("""
            SELECT id, display_name, phone_number, created_at, updated_at
            FROM rider_profiles
            WHERE tenant_id = :tenantId AND account_id = :accountId
            """)
        .param("tenantId", tenantId)
        .param("accountId", accountId)
        .query(this::map)
        .optional();
  }

  @Override
  public void insert(UUID tenantId, UUID accountId, RiderProfile profile) {
    jdbc.sql("""
            INSERT INTO rider_profiles
              (id, tenant_id, account_id, display_name, phone_number, created_at, updated_at)
            VALUES (:id, :tenantId, :accountId, :displayName, :phoneNumber, :createdAt, :updatedAt)
            """)
        .param("id", profile.id())
        .param("tenantId", tenantId)
        .param("accountId", accountId)
        .param("displayName", profile.displayName())
        .param("phoneNumber", profile.phoneNumber())
        .param("createdAt", Timestamp.from(profile.createdAt()))
        .param("updatedAt", Timestamp.from(profile.updatedAt()))
        .update();
  }

  @Override
  public boolean update(
      UUID tenantId, UUID accountId, String displayName, String phoneNumber, Instant updatedAt) {
    return jdbc.sql("""
            UPDATE rider_profiles
            SET display_name = :displayName, phone_number = :phoneNumber, updated_at = :updatedAt
            WHERE tenant_id = :tenantId AND account_id = :accountId
            """)
        .param("tenantId", tenantId)
        .param("accountId", accountId)
        .param("displayName", displayName)
        .param("phoneNumber", phoneNumber)
        .param("updatedAt", Timestamp.from(updatedAt))
        .update() == 1;
  }

  private RiderProfile map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new RiderProfile(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("display_name"),
        resultSet.getString("phone_number"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }
}

package in.rsh.cab.driver.internal.persistence;

import in.rsh.cab.driver.DriverProfile;
import in.rsh.cab.driver.DriverStatus;
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
public class JdbcDriverProfileRepository implements DriverProfileRepository {

  private static final String SELECT = """
      SELECT id, account_id, legal_name, phone_number, status, created_at, updated_at
      FROM driver_profiles
      """;
  private final JdbcClient jdbc;

  public JdbcDriverProfileRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(UUID tenantId, DriverProfile profile) {
    jdbc.sql("""
            INSERT INTO driver_profiles
              (id, tenant_id, account_id, legal_name, phone_number, status, created_at, updated_at)
            VALUES
              (:id, :tenantId, :accountId, :legalName, :phoneNumber, :status, :createdAt, :updatedAt)
            """)
        .param("id", profile.id())
        .param("tenantId", tenantId)
        .param("accountId", profile.accountId())
        .param("legalName", profile.legalName())
        .param("phoneNumber", profile.phoneNumber())
        .param("status", profile.status().name())
        .param("createdAt", Timestamp.from(profile.createdAt()))
        .param("updatedAt", Timestamp.from(profile.updatedAt()))
        .update();
  }

  @Override
  public Optional<DriverProfile> findByTenantIdAndId(UUID tenantId, UUID id) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id = :id")
        .param("tenantId", tenantId).param("id", id).query(this::map).optional();
  }

  @Override
  public Optional<DriverProfile> findByTenantIdAndAccountId(UUID tenantId, UUID accountId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND account_id = :accountId")
        .param("tenantId", tenantId).param("accountId", accountId).query(this::map).optional();
  }

  @Override
  public List<DriverProfile> findAllByTenantId(UUID tenantId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId ORDER BY created_at, id")
        .param("tenantId", tenantId).query(this::map).list();
  }

  @Override
  public boolean updateStatus(
      UUID tenantId, UUID id, DriverStatus expected, DriverStatus status, Instant updatedAt) {
    return jdbc.sql("""
            UPDATE driver_profiles SET status = :status, updated_at = :updatedAt
            WHERE tenant_id = :tenantId AND id = :id AND status = :expected
            """)
        .param("tenantId", tenantId).param("id", id)
        .param("expected", expected.name()).param("status", status.name())
        .param("updatedAt", Timestamp.from(updatedAt)).update() == 1;
  }

  @Override
  public boolean updateOwn(
      UUID tenantId, UUID accountId, String legalName, String phoneNumber, Instant updatedAt) {
    return jdbc.sql("""
            UPDATE driver_profiles
            SET legal_name = :legalName, phone_number = :phoneNumber, updated_at = :updatedAt
            WHERE tenant_id = :tenantId AND account_id = :accountId
            """)
        .param("tenantId", tenantId).param("accountId", accountId)
        .param("legalName", legalName).param("phoneNumber", phoneNumber)
        .param("updatedAt", Timestamp.from(updatedAt)).update() == 1;
  }

  private DriverProfile map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DriverProfile(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("account_id", UUID.class),
        resultSet.getString("legal_name"),
        resultSet.getString("phone_number"),
        DriverStatus.valueOf(resultSet.getString("status")),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }
}

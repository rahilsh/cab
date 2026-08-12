package in.rsh.cab.geography.internal.persistence;

import in.rsh.cab.geography.ServiceArea;
import in.rsh.cab.geography.GeoPoint;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcServiceAreaRepository implements ServiceAreaRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public JdbcServiceAreaRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean existsByTenantIdAndSlugOrName(UUID tenantId, String slug, String name) {
    return jdbc.sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM service_areas
              WHERE tenant_id = :tenantId AND (slug = :slug OR name = :name)
            )
            """)
        .param("tenantId", tenantId)
        .param("slug", slug)
        .param("name", name)
        .query(Boolean.class)
        .single();
  }

  @Override
  public void insert(UUID tenantId, ServiceArea serviceArea) {
    jdbc.sql(
            """
            INSERT INTO service_areas
              (id, tenant_id, slug, name, status, timezone, boundary, created_at, updated_at)
            VALUES
              (:id, :tenantId, :slug, :name, :status, :timezone,
               ST_Multi(ST_GeomFromGeoJSON(:boundary)), :createdAt, :updatedAt)
            """)
        .param("id", serviceArea.id())
        .param("tenantId", tenantId)
        .param("slug", serviceArea.slug())
        .param("name", serviceArea.name())
        .param("status", serviceArea.status())
        .param("timezone", serviceArea.timezone())
        .param("boundary", serviceArea.boundary().toString())
        .param("createdAt", Timestamp.from(serviceArea.createdAt()))
        .param("updatedAt", Timestamp.from(serviceArea.updatedAt()))
        .update();
  }

  @Override
  public List<ServiceArea> findAllByTenantId(UUID tenantId) {
    return jdbc.sql(
            """
            SELECT id, slug, name, status, timezone, ST_AsGeoJSON(boundary) AS boundary,
                   created_at, updated_at
            FROM service_areas
            WHERE tenant_id = :tenantId
            ORDER BY name, id
            """)
        .param("tenantId", tenantId)
        .query(this::map)
        .list();
  }

  @Override
  public boolean coversRoute(UUID tenantId, GeoPoint pickup, GeoPoint dropoff) {
    return jdbc.sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM service_areas
              WHERE tenant_id = :tenantId AND status = 'ACTIVE'
                AND ST_Covers(boundary, ST_SetSRID(ST_MakePoint(:pickupLongitude, :pickupLatitude), 4326))
                AND ST_Covers(boundary, ST_SetSRID(ST_MakePoint(:dropoffLongitude, :dropoffLatitude), 4326))
            )
            """)
        .param("tenantId", tenantId)
        .param("pickupLongitude", pickup.longitude())
        .param("pickupLatitude", pickup.latitude())
        .param("dropoffLongitude", dropoff.longitude())
        .param("dropoffLatitude", dropoff.latitude())
        .query(Boolean.class)
        .single();
  }

  private ServiceArea map(ResultSet resultSet, int rowNumber) throws SQLException {
    try {
      JsonNode boundary = objectMapper.readTree(resultSet.getString("boundary"));
      return new ServiceArea(
          resultSet.getObject("id", UUID.class),
          resultSet.getString("slug"),
          resultSet.getString("name"),
          resultSet.getString("status"),
          resultSet.getString("timezone"),
          boundary,
          resultSet.getTimestamp("created_at").toInstant(),
          resultSet.getTimestamp("updated_at").toInstant());
    } catch (JacksonException exception) {
      throw new SQLException("Stored service area boundary is invalid", exception);
    }
  }
}

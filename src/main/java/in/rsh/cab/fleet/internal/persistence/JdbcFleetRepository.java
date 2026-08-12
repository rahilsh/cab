package in.rsh.cab.fleet.internal.persistence;

import in.rsh.cab.driver.DriverStatus;
import in.rsh.cab.fleet.DriverShift;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.SupplyCandidate;
import in.rsh.cab.fleet.Vehicle;
import in.rsh.cab.fleet.VehicleStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFleetRepository implements FleetRepository {

  private static final String VEHICLE_SELECT = """
      SELECT id, registration, service_class, capacity, status, version, created_at, updated_at
      FROM vehicles
      """;
  private static final String SHIFT_SELECT = """
      SELECT s.id, s.driver_id, s.vehicle_id, s.status, s.version, s.created_at,
             s.updated_at, s.available_at, s.closed_at
      FROM driver_shifts s
      JOIN driver_profiles d ON d.tenant_id = s.tenant_id AND d.id = s.driver_id
      """;
  private final JdbcClient jdbc;

  public JdbcFleetRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insertVehicle(UUID tenantId, Vehicle vehicle) {
    jdbc.sql("""
            INSERT INTO vehicles
              (id, tenant_id, registration, service_class, capacity, status, version,
               created_at, updated_at)
            VALUES
              (:id, :tenantId, :registration, :serviceClass, :capacity, :status, :version,
               :createdAt, :updatedAt)
            """)
        .param("id", vehicle.id()).param("tenantId", tenantId)
        .param("registration", vehicle.registration()).param("serviceClass", vehicle.serviceClass())
        .param("capacity", vehicle.capacity()).param("status", vehicle.status().name())
        .param("version", vehicle.version()).param("createdAt", Timestamp.from(vehicle.createdAt()))
        .param("updatedAt", Timestamp.from(vehicle.updatedAt())).update();
  }

  @Override
  public Optional<Vehicle> findVehicle(UUID tenantId, UUID id) {
    return jdbc.sql(VEHICLE_SELECT + " WHERE tenant_id = :tenantId AND id = :id")
        .param("tenantId", tenantId).param("id", id).query(this::mapVehicle).optional();
  }

  @Override
  public List<Vehicle> findVehicles(UUID tenantId) {
    return jdbc.sql(VEHICLE_SELECT + " WHERE tenant_id = :tenantId ORDER BY registration, id")
        .param("tenantId", tenantId).query(this::mapVehicle).list();
  }

  @Override
  public boolean updateVehicle(UUID tenantId, Vehicle vehicle, long expectedVersion) {
    return jdbc.sql("""
            UPDATE vehicles
            SET registration = :registration, service_class = :serviceClass,
                capacity = :capacity, status = :status, version = :version, updated_at = :updatedAt
            WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
            """)
        .param("tenantId", tenantId).param("id", vehicle.id())
        .param("registration", vehicle.registration()).param("serviceClass", vehicle.serviceClass())
        .param("capacity", vehicle.capacity()).param("status", vehicle.status().name())
        .param("version", vehicle.version()).param("updatedAt", Timestamp.from(vehicle.updatedAt()))
        .param("expectedVersion", expectedVersion).update() == 1;
  }

  @Override
  public Optional<DriverStatus> findDriverStatus(UUID tenantId, UUID driverId, UUID accountId) {
    return jdbc.sql("""
            SELECT status FROM driver_profiles
            WHERE tenant_id = :tenantId AND id = :driverId AND account_id = :accountId
            """)
        .param("tenantId", tenantId).param("driverId", driverId).param("accountId", accountId)
        .query(String.class).optional().map(DriverStatus::valueOf);
  }

  @Override
  public void insertShift(UUID tenantId, DriverShift shift) {
    jdbc.sql("""
            INSERT INTO driver_shifts
              (id, tenant_id, driver_id, vehicle_id, status, version, created_at, updated_at)
            VALUES
              (:id, :tenantId, :driverId, :vehicleId, :status, :version, :createdAt, :updatedAt)
            """)
        .param("id", shift.id()).param("tenantId", tenantId).param("driverId", shift.driverId())
        .param("vehicleId", shift.vehicleId()).param("status", shift.status().name())
        .param("version", shift.version()).param("createdAt", Timestamp.from(shift.createdAt()))
        .param("updatedAt", Timestamp.from(shift.updatedAt())).update();
  }

  @Override
  public Optional<DriverShift> findShift(UUID tenantId, UUID shiftId, UUID accountId) {
    return jdbc.sql(SHIFT_SELECT + """
            WHERE s.tenant_id = :tenantId AND s.id = :shiftId AND d.account_id = :accountId
            """)
        .param("tenantId", tenantId).param("shiftId", shiftId).param("accountId", accountId)
        .query(this::mapShift).optional();
  }

  @Override
  public Optional<DriverShift> findShift(UUID tenantId, UUID shiftId) {
    return jdbc.sql(SHIFT_SELECT + " WHERE s.tenant_id = :tenantId AND s.id = :shiftId")
        .param("tenantId", tenantId).param("shiftId", shiftId).query(this::mapShift).optional();
  }

  @Override
  public List<DriverShift> findShifts(UUID tenantId, UUID accountId) {
    return jdbc.sql(SHIFT_SELECT + """
            WHERE s.tenant_id = :tenantId AND d.account_id = :accountId
            ORDER BY s.created_at DESC, s.id
            """)
        .param("tenantId", tenantId).param("accountId", accountId).query(this::mapShift).list();
  }

  @Override
  public boolean updateShift(UUID tenantId, DriverShift shift, long expectedVersion) {
    return jdbc.sql("""
            UPDATE driver_shifts
            SET status = :status, version = :version, updated_at = :updatedAt,
                available_at = :availableAt, closed_at = :closedAt
            WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
            """)
        .param("tenantId", tenantId).param("id", shift.id()).param("status", shift.status().name())
        .param("version", shift.version()).param("updatedAt", Timestamp.from(shift.updatedAt()))
        .param("availableAt", timestamp(shift.availableAt())).param("closedAt", timestamp(shift.closedAt()))
        .param("expectedVersion", expectedVersion).update() == 1;
  }

  @Override
  public boolean transitionShift(
      UUID tenantId, UUID shiftId, ShiftStatus expected, ShiftStatus next, java.time.Instant now) {
    return jdbc.sql("""
            UPDATE driver_shifts
            SET status = :next, version = version + 1, updated_at = :now,
                available_at = CASE WHEN :next = 'AVAILABLE' THEN :now ELSE available_at END
            WHERE tenant_id = :tenantId AND id = :shiftId AND status = :expected
            """)
        .param("tenantId", tenantId).param("shiftId", shiftId)
        .param("expected", expected.name()).param("next", next.name())
        .param("now", Timestamp.from(now)).update() == 1;
  }

  @Override
  public List<SupplyCandidate> findAvailableCandidates(
      UUID tenantId, List<UUID> shiftIds, String serviceClass) {
    if (shiftIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql("""
            SELECT s.id, s.driver_id, s.vehicle_id
            FROM driver_shifts s
            JOIN vehicles v ON v.tenant_id = s.tenant_id AND v.id = s.vehicle_id
            JOIN driver_profiles d ON d.tenant_id = s.tenant_id AND d.id = s.driver_id
            WHERE s.tenant_id = :tenantId AND s.id IN (:shiftIds) AND s.status = 'AVAILABLE'
              AND v.status = 'ACTIVE' AND v.service_class = :serviceClass AND d.status = 'APPROVED'
            """)
        .param("tenantId", tenantId).param("shiftIds", shiftIds).param("serviceClass", serviceClass)
        .query((rs, row) -> new SupplyCandidate(rs.getObject("id", UUID.class),
            rs.getObject("driver_id", UUID.class), rs.getObject("vehicle_id", UUID.class))).list();
  }

  private Vehicle mapVehicle(ResultSet resultSet, int rowNumber) throws SQLException {
    return new Vehicle(
        resultSet.getObject("id", UUID.class), resultSet.getString("registration"),
        resultSet.getString("service_class"), resultSet.getInt("capacity"),
        VehicleStatus.valueOf(resultSet.getString("status")), resultSet.getLong("version"),
        resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant());
  }

  private DriverShift mapShift(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DriverShift(
        resultSet.getObject("id", UUID.class), resultSet.getObject("driver_id", UUID.class),
        resultSet.getObject("vehicle_id", UUID.class), ShiftStatus.valueOf(resultSet.getString("status")),
        resultSet.getLong("version"), resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant(), instant(resultSet, "available_at"),
        instant(resultSet, "closed_at"));
  }

  private Timestamp timestamp(java.time.Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private java.time.Instant instant(ResultSet resultSet, String name) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(name);
    return timestamp == null ? null : timestamp.toInstant();
  }
}

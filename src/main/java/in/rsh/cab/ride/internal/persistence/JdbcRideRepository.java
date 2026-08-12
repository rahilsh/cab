package in.rsh.cab.ride.internal.persistence;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.ride.CancellationActor;
import in.rsh.cab.ride.Ride;
import in.rsh.cab.ride.RideStatus;
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
public class JdbcRideRepository implements RideRepository {

  private static final String SELECT = """
      SELECT r.id, r.rider_account_id, r.quote_id, r.product_id,
             ST_Y(r.pickup) AS pickup_latitude, ST_X(r.pickup) AS pickup_longitude,
             ST_Y(r.dropoff) AS dropoff_latitude, ST_X(r.dropoff) AS dropoff_longitude,
             r.fare_minor, r.currency, r.driver_id, r.vehicle_id, r.driver_shift_id,
             r.status, r.cancellation_actor, r.cancellation_reason, r.requested_at,
             r.assigned_at, r.arrived_at, r.started_at, r.completed_at, r.cancelled_at,
             r.updated_at, r.version
      FROM rides r
      """;
  private final JdbcClient jdbc;

  public JdbcRideRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(UUID tenantId, Ride ride) {
    jdbc.sql("""
            INSERT INTO rides
              (id, tenant_id, rider_account_id, quote_id, product_id, pickup, dropoff,
               fare_minor, currency, status, requested_at, updated_at, version)
            VALUES (:id, :tenantId, :riderAccountId, :quoteId, :productId,
              ST_SetSRID(ST_MakePoint(:pickupLongitude, :pickupLatitude), 4326),
              ST_SetSRID(ST_MakePoint(:dropoffLongitude, :dropoffLatitude), 4326),
              :fareMinor, :currency, :status, :requestedAt, :updatedAt, :version)
            """)
        .param("id", ride.id()).param("tenantId", tenantId)
        .param("riderAccountId", ride.riderAccountId()).param("quoteId", ride.quoteId())
        .param("productId", ride.productId()).param("pickupLongitude", ride.pickup().longitude())
        .param("pickupLatitude", ride.pickup().latitude())
        .param("dropoffLongitude", ride.dropoff().longitude())
        .param("dropoffLatitude", ride.dropoff().latitude()).param("fareMinor", ride.fareMinor())
        .param("currency", ride.currency()).param("status", ride.status().name())
        .param("requestedAt", Timestamp.from(ride.requestedAt()))
        .param("updatedAt", Timestamp.from(ride.updatedAt())).param("version", ride.version()).update();
  }

  @Override
  public Optional<Ride> find(UUID tenantId, UUID rideId) {
    return jdbc.sql(SELECT + " WHERE r.tenant_id = :tenantId AND r.id = :rideId")
        .param("tenantId", tenantId).param("rideId", rideId).query(this::map).optional();
  }

  @Override
  public Optional<Ride> findOwn(UUID tenantId, UUID riderAccountId, UUID rideId) {
    return jdbc.sql(SELECT + """
            WHERE r.tenant_id = :tenantId AND r.rider_account_id = :accountId AND r.id = :rideId
            """)
        .param("tenantId", tenantId).param("accountId", riderAccountId).param("rideId", rideId)
        .query(this::map).optional();
  }

  @Override
  public Optional<Ride> findAssignedToDriver(UUID tenantId, UUID driverAccountId, UUID rideId) {
    return jdbc.sql(SELECT + """
            JOIN driver_profiles d ON d.tenant_id = r.tenant_id AND d.id = r.driver_id
            WHERE r.tenant_id = :tenantId AND d.account_id = :accountId AND r.id = :rideId
            """)
        .param("tenantId", tenantId).param("accountId", driverAccountId).param("rideId", rideId)
        .query(this::map).optional();
  }

  @Override
  public List<Ride> findOwn(UUID tenantId, UUID riderAccountId) {
    return jdbc.sql(SELECT + """
            WHERE r.tenant_id = :tenantId AND r.rider_account_id = :accountId
            ORDER BY r.requested_at DESC, r.id
            """)
        .param("tenantId", tenantId).param("accountId", riderAccountId).query(this::map).list();
  }

  @Override
  public boolean update(UUID tenantId, Ride ride, long expectedVersion) {
    return jdbc.sql("""
            UPDATE rides SET driver_id = :driverId, vehicle_id = :vehicleId,
              driver_shift_id = :shiftId, status = :status,
              cancellation_actor = :cancellationActor, cancellation_reason = :cancellationReason,
              assigned_at = :assignedAt, arrived_at = :arrivedAt, started_at = :startedAt,
              completed_at = :completedAt, cancelled_at = :cancelledAt,
              updated_at = :updatedAt, version = :version
            WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
            """)
        .param("tenantId", tenantId).param("id", ride.id()).param("driverId", ride.driverId())
        .param("vehicleId", ride.vehicleId()).param("shiftId", ride.driverShiftId())
        .param("status", ride.status().name())
        .param("cancellationActor", ride.cancellationActor() == null ? null : ride.cancellationActor().name())
        .param("cancellationReason", ride.cancellationReason())
        .param("assignedAt", timestamp(ride.assignedAt())).param("arrivedAt", timestamp(ride.arrivedAt()))
        .param("startedAt", timestamp(ride.startedAt())).param("completedAt", timestamp(ride.completedAt()))
        .param("cancelledAt", timestamp(ride.cancelledAt())).param("updatedAt", Timestamp.from(ride.updatedAt()))
        .param("version", ride.version()).param("expectedVersion", expectedVersion).update() == 1;
  }

  @Override
  public void appendHistory(
      UUID tenantId, UUID rideId, RideStatus from, RideStatus to, UUID actorAccountId,
      String reason, Instant occurredAt) {
    jdbc.sql("""
            INSERT INTO ride_status_history
              (id, tenant_id, ride_id, from_status, to_status, actor_account_id, reason, occurred_at)
            VALUES (:id, :tenantId, :rideId, :fromStatus, :toStatus, :actorId, :reason, :occurredAt)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId).param("rideId", rideId)
        .param("fromStatus", from == null ? null : from.name()).param("toStatus", to.name())
        .param("actorId", actorAccountId).param("reason", reason)
        .param("occurredAt", Timestamp.from(occurredAt)).update();
  }

  private Ride map(ResultSet rs, int rowNumber) throws SQLException {
    String actor = rs.getString("cancellation_actor");
    return new Ride(rs.getObject("id", UUID.class), rs.getObject("rider_account_id", UUID.class),
        rs.getObject("quote_id", UUID.class), rs.getObject("product_id", UUID.class),
        new GeoPoint(rs.getDouble("pickup_latitude"), rs.getDouble("pickup_longitude")),
        new GeoPoint(rs.getDouble("dropoff_latitude"), rs.getDouble("dropoff_longitude")),
        rs.getLong("fare_minor"), rs.getString("currency"), rs.getObject("driver_id", UUID.class),
        rs.getObject("vehicle_id", UUID.class), rs.getObject("driver_shift_id", UUID.class),
        RideStatus.valueOf(rs.getString("status")), actor == null ? null : CancellationActor.valueOf(actor),
        rs.getString("cancellation_reason"), rs.getTimestamp("requested_at").toInstant(),
        instant(rs, "assigned_at"), instant(rs, "arrived_at"), instant(rs, "started_at"),
        instant(rs, "completed_at"), instant(rs, "cancelled_at"),
        rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
  }

  private Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}

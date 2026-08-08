package in.rsh.cab.dispatch.internal.persistence;

import in.rsh.cab.dispatch.DriverOffer;
import in.rsh.cab.dispatch.DriverOfferStatus;
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
public class JdbcDispatchRepository implements DispatchRepository {

  private static final String OFFER_SELECT = """
      SELECT o.id, o.attempt_id, o.ride_id, o.shift_id, o.driver_id, o.vehicle_id,
             o.status, o.expires_at, o.responded_at, o.created_at, o.updated_at, o.version
      FROM driver_offers o
      JOIN driver_profiles d ON d.tenant_id = o.tenant_id AND d.id = o.driver_id
      """;
  private final JdbcClient jdbc;

  public JdbcDispatchRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insertAttempt(
      UUID tenantId, UUID attemptId, UUID rideId, int radiusMeters, int candidateLimit,
      int candidateCount, String status, Instant now) {
    jdbc.sql("""
            INSERT INTO dispatch_attempts
              (id, tenant_id, ride_id, status, search_radius_meters, candidate_limit,
               candidate_count, started_at, completed_at, updated_at, version)
            VALUES (:id, :tenantId, :rideId, :status, :radius, :candidateLimit,
              :candidateCount, :now,
              CASE WHEN :status = 'EXHAUSTED' THEN CAST(:now AS timestamptz) ELSE NULL::timestamptz END,
              :now, 0)
            """)
        .param("id", attemptId).param("tenantId", tenantId).param("rideId", rideId)
        .param("status", status).param("radius", radiusMeters).param("candidateLimit", candidateLimit)
        .param("candidateCount", candidateCount).param("now", Timestamp.from(now)).update();
  }

  @Override
  public void insertOffer(UUID tenantId, DriverOffer offer) {
    jdbc.sql("""
            INSERT INTO driver_offers
              (id, tenant_id, attempt_id, ride_id, shift_id, driver_id, vehicle_id, status,
               expires_at, created_at, updated_at, version)
            VALUES (:id, :tenantId, :attemptId, :rideId, :shiftId, :driverId, :vehicleId,
              :status, :expiresAt, :createdAt, :updatedAt, :version)
            """)
        .param("id", offer.id()).param("tenantId", tenantId).param("attemptId", offer.attemptId())
        .param("rideId", offer.rideId()).param("shiftId", offer.shiftId())
        .param("driverId", offer.driverId()).param("vehicleId", offer.vehicleId())
        .param("status", offer.status().name()).param("expiresAt", Timestamp.from(offer.expiresAt()))
        .param("createdAt", Timestamp.from(offer.createdAt())).param("updatedAt", Timestamp.from(offer.updatedAt()))
        .param("version", offer.version()).update();
  }

  @Override
  public List<DriverOffer> findOwnOffers(UUID tenantId, UUID driverAccountId, Instant now) {
    return jdbc.sql(OFFER_SELECT + """
            WHERE o.tenant_id = :tenantId AND d.account_id = :accountId
              AND o.status = 'PENDING' AND o.expires_at > :now
            ORDER BY o.expires_at, o.id
            """)
        .param("tenantId", tenantId).param("accountId", driverAccountId)
        .param("now", Timestamp.from(now)).query(this::map).list();
  }

  @Override
  public Optional<DriverOffer> lockOwnOffer(UUID tenantId, UUID driverAccountId, UUID offerId) {
    return jdbc.sql(OFFER_SELECT + """
            WHERE o.tenant_id = :tenantId AND d.account_id = :accountId AND o.id = :offerId
            FOR UPDATE OF o
            """)
        .param("tenantId", tenantId).param("accountId", driverAccountId).param("offerId", offerId)
        .query(this::map).optional();
  }

  @Override
  public boolean respond(UUID tenantId, UUID offerId, String expected, String status, Instant now) {
    return jdbc.sql("""
            UPDATE driver_offers SET status = :status, responded_at = :now,
              updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND id = :offerId AND status = :expected
            """)
        .param("tenantId", tenantId).param("offerId", offerId).param("expected", expected)
        .param("status", status).param("now", Timestamp.from(now)).update() == 1;
  }

  @Override
  public void expireSiblings(UUID tenantId, UUID rideId, UUID acceptedOfferId, Instant now) {
    jdbc.sql("""
            UPDATE driver_offers SET status = 'EXPIRED', responded_at = :now,
              updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND ride_id = :rideId AND id <> :acceptedId
              AND status = 'PENDING'
            """)
        .param("tenantId", tenantId).param("rideId", rideId).param("acceptedId", acceptedOfferId)
        .param("now", Timestamp.from(now)).update();
  }

  @Override
  public void completeAttempt(UUID tenantId, UUID attemptId, String status, Instant now) {
    jdbc.sql("""
            UPDATE dispatch_attempts SET status = :status, completed_at = :now,
              updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND id = :attemptId AND status IN ('SEARCHING', 'OFFERED')
            """)
        .param("tenantId", tenantId).param("attemptId", attemptId).param("status", status)
        .param("now", Timestamp.from(now)).update();
  }

  @Override
  public void cancelRide(UUID tenantId, UUID rideId, Instant now) {
    jdbc.sql("""
            UPDATE driver_offers SET status = 'EXPIRED', responded_at = :now,
              updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND ride_id = :rideId AND status = 'PENDING'
            """)
        .param("tenantId", tenantId).param("rideId", rideId)
        .param("now", Timestamp.from(now)).update();
    jdbc.sql("""
            UPDATE dispatch_attempts SET status = 'CANCELLED', completed_at = :now,
              updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND ride_id = :rideId AND status IN ('SEARCHING', 'OFFERED')
            """)
        .param("tenantId", tenantId).param("rideId", rideId)
        .param("now", Timestamp.from(now)).update();
  }

  private DriverOffer map(ResultSet rs, int rowNumber) throws SQLException {
    Timestamp responded = rs.getTimestamp("responded_at");
    return new DriverOffer(rs.getObject("id", UUID.class), rs.getObject("attempt_id", UUID.class),
        rs.getObject("ride_id", UUID.class), rs.getObject("shift_id", UUID.class),
        rs.getObject("driver_id", UUID.class), rs.getObject("vehicle_id", UUID.class),
        DriverOfferStatus.valueOf(rs.getString("status")), rs.getTimestamp("expires_at").toInstant(),
        responded == null ? null : responded.toInstant(), rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
  }
}

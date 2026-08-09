package in.rsh.cab.rating.internal.persistence;

import in.rsh.cab.rating.Rating;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRatingRepository implements RatingRepository {

  private final JdbcClient jdbc;

  public JdbcRatingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<RideParticipants> completedRideParticipants(UUID tenantId, UUID rideId) {
    return jdbc.sql("""
            SELECT r.rider_account_id, d.account_id driver_account_id
            FROM rides r JOIN driver_profiles d
              ON d.tenant_id = r.tenant_id AND d.id = r.driver_id
            WHERE r.tenant_id = :tenantId AND r.id = :rideId AND r.status = 'COMPLETED'
            """)
        .param("tenantId", tenantId).param("rideId", rideId)
        .query((rs, row) -> new RideParticipants(
            rs.getObject("rider_account_id", UUID.class),
            rs.getObject("driver_account_id", UUID.class)))
        .optional();
  }

  @Override
  public void insert(UUID tenantId, Rating rating) {
    jdbc.sql("""
            INSERT INTO ratings
              (id, tenant_id, ride_id, reviewer_account_id, reviewee_account_id,
               reviewer_role, reviewee_role, score, comment, moderation_status, created_at)
            VALUES (:id, :tenantId, :rideId, :reviewerId, :revieweeId,
                    :reviewerRole, :revieweeRole, :score, :comment, :status, :createdAt)
            """)
        .param("id", rating.id()).param("tenantId", tenantId).param("rideId", rating.rideId())
        .param("reviewerId", rating.reviewerAccountId())
        .param("revieweeId", rating.revieweeAccountId())
        .param("reviewerRole", rating.reviewerRole()).param("revieweeRole", rating.revieweeRole())
        .param("score", rating.score()).param("comment", rating.comment())
        .param("status", rating.moderationStatus())
        .param("createdAt", Timestamp.from(rating.createdAt())).update();
  }

  @Override
  public Optional<Rating> findOwn(UUID tenantId, UUID accountId, UUID ratingId) {
    return jdbc.sql("""
            SELECT id, ride_id, reviewer_account_id, reviewee_account_id, reviewer_role,
                   reviewee_role, score, comment, moderation_status, created_at
            FROM ratings
            WHERE tenant_id = :tenantId AND id = :ratingId
              AND (reviewer_account_id = :accountId OR reviewee_account_id = :accountId)
            """)
        .param("tenantId", tenantId).param("ratingId", ratingId).param("accountId", accountId)
        .query(this::map).optional();
  }

  private Rating map(ResultSet rs, int row) throws SQLException {
    return new Rating(rs.getObject("id", UUID.class), rs.getObject("ride_id", UUID.class),
        rs.getObject("reviewer_account_id", UUID.class),
        rs.getObject("reviewee_account_id", UUID.class), rs.getString("reviewer_role"),
        rs.getString("reviewee_role"), rs.getInt("score"), rs.getString("comment"),
        rs.getString("moderation_status"), rs.getTimestamp("created_at").toInstant());
  }
}

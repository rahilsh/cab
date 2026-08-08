package in.rsh.cab.rating;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.rating.internal.persistence.RatingRepository;
import in.rsh.cab.rating.internal.persistence.RatingRepository.RideParticipants;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RatingService {

  private final RatingRepository ratings;
  private final OutboxService outbox;
  private final AuditService audit;
  private final ObjectMapper json;
  private final Clock clock;

  public RatingService(
      RatingRepository ratings, OutboxService outbox, AuditService audit,
      ObjectMapper json, Clock clock) {
    this.ratings = ratings;
    this.outbox = outbox;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public Rating create(UUID rideId, int score, String comment) {
    TenantContext context = TenantContext.require();
    if (score < 1 || score > 5) {
      throw new InvalidRequestException("Rating score must be between 1 and 5");
    }
    RideParticipants participants = ratings.completedRideParticipants(context.tenantId(), rideId)
        .orElseThrow(() -> new NotFoundException("Completed ride not found"));
    String reviewerRole;
    UUID reviewee;
    String revieweeRole;
    if (context.accountId().equals(participants.riderAccountId())
        && context.roles().contains(TenantRole.RIDER)) {
      reviewerRole = "RIDER";
      reviewee = participants.driverAccountId();
      revieweeRole = "DRIVER";
    } else if (context.accountId().equals(participants.driverAccountId())
        && context.roles().contains(TenantRole.DRIVER)) {
      reviewerRole = "DRIVER";
      reviewee = participants.riderAccountId();
      revieweeRole = "RIDER";
    } else {
      throw new TenantAccessDeniedException("Only completed ride participants can rate");
    }
    Rating rating = new Rating(UUID.randomUUID(), rideId, context.accountId(), reviewee,
        reviewerRole, revieweeRole, score, comment, "PUBLISHED", clock.instant());
    try {
      ratings.insert(context.tenantId(), rating);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Reviewer already rated this ride");
    }
    outbox.append(context.tenantId(), "rating", rating.id(), 0, "rating.created", 1,
        json.valueToTree(new RatingEvent(rating.id(), rideId, reviewee, score)), null);
    audit.record(context.tenantId(), context.accountId(), "rating.create", "rating", rating.id(),
        "SUCCESS", json.valueToTree(new RatingAudit(rideId, score)));
    return rating;
  }

  private record RatingEvent(UUID ratingId, UUID rideId, UUID revieweeAccountId, int score) {}

  private record RatingAudit(UUID rideId, int score) {}
}

package in.rsh.cab.dispatch;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.dispatch.internal.persistence.DispatchRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.SupplyCandidate;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.location.LiveLocationStore;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.pricing.internal.persistence.PricingRepository;
import in.rsh.cab.ride.Ride;
import in.rsh.cab.ride.RideStatus;
import in.rsh.cab.ride.internal.persistence.RideRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DispatchService {

  private final DispatchRepository dispatch;
  private final RideRepository rides;
  private final FleetRepository fleet;
  private final PricingRepository pricing;
  private final LiveLocationStore locations;
  private final OutboxService outbox;
  private final AuditService audit;
  private final ObjectMapper json;
  private final Clock clock;
  private final int radiusMeters;
  private final int candidateLimit;
  private final Duration locationMaxAge;
  private final Duration offerTtl;

  public DispatchService(
      DispatchRepository dispatch, RideRepository rides, FleetRepository fleet,
      PricingRepository pricing, LiveLocationStore locations, OutboxService outbox,
      AuditService audit, ObjectMapper json, Clock clock,
      @Value("${dispatch.search-radius-meters:5000}") int radiusMeters,
      @Value("${dispatch.candidate-limit:10}") int candidateLimit,
      @Value("${dispatch.location-max-age:PT2M}") Duration locationMaxAge,
      @Value("${dispatch.offer-ttl:PT30S}") Duration offerTtl) {
    this.dispatch = dispatch;
    this.rides = rides;
    this.fleet = fleet;
    this.pricing = pricing;
    this.locations = locations;
    this.outbox = outbox;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
    this.radiusMeters = radiusMeters;
    this.candidateLimit = candidateLimit;
    this.locationMaxAge = locationMaxAge;
    this.offerTtl = offerTtl;
  }

  @Transactional
  public List<DriverOffer> start(UUID rideId, long version) {
    TenantContext context = requireAny(TenantRole.TENANT_ADMIN, TenantRole.DISPATCHER);
    Ride current = rides.find(context.tenantId(), rideId)
        .orElseThrow(() -> new NotFoundException("Ride not found"));
    if (current.version() != version) {
      throw new ConflictException("Ride version is stale");
    }
    Instant now = clock.instant();
    Ride matching = current.matching(now);
    if (!rides.update(context.tenantId(), matching, version)) {
      throw new ConflictException("Ride changed concurrently");
    }
    rides.appendHistory(context.tenantId(), rideId, current.status(), matching.status(),
        context.accountId(), null, now);
    String serviceClass = pricing.findProduct(context.tenantId(), matching.productId())
        .orElseThrow(() -> new NotFoundException("Ride product not found")).serviceClass();
    List<UUID> nearby = locations.nearby(context.tenantId(), matching.pickup(), radiusMeters,
        candidateLimit, now, locationMaxAge);
    List<SupplyCandidate> candidates = fleet.findAvailableCandidates(
        context.tenantId(), nearby, serviceClass);
    UUID attemptId = UUID.randomUUID();
    String attemptStatus = candidates.isEmpty() ? "EXHAUSTED" : "OFFERED";
    dispatch.insertAttempt(context.tenantId(), attemptId, rideId, radiusMeters, candidateLimit,
        candidates.size(), attemptStatus, now);
    if (candidates.isEmpty()) {
      Ride noDriver = matching.noDriver(now);
      if (!rides.update(context.tenantId(), noDriver, matching.version())) {
        throw new ConflictException("Ride changed concurrently");
      }
      rides.appendHistory(context.tenantId(), rideId, matching.status(), noDriver.status(),
          context.accountId(), "No eligible nearby drivers", now);
      emit(context, noDriver, "ride.no_driver", "dispatch.exhausted");
      return List.of();
    }
    List<DriverOffer> offers = candidates.stream().map(candidate -> new DriverOffer(
        UUID.randomUUID(), attemptId, rideId, candidate.shiftId(), candidate.driverId(),
        candidate.vehicleId(), DriverOfferStatus.PENDING, now.plus(offerTtl), null, now, now, 0))
        .toList();
    offers.forEach(offer -> dispatch.insertOffer(context.tenantId(), offer));
    emit(context, matching, "dispatch.offers_created", "dispatch.start");
    return offers;
  }

  @Transactional(readOnly = true)
  public List<DriverOffer> listOwnOffers() {
    TenantContext context = requireAny(TenantRole.DRIVER);
    return dispatch.findOwnOffers(context.tenantId(), context.accountId(), clock.instant());
  }

  @Transactional
  public Ride accept(UUID offerId) {
    TenantContext context = requireAny(TenantRole.DRIVER);
    Instant now = clock.instant();
    DriverOffer offer = dispatch.lockOwnOffer(context.tenantId(), context.accountId(), offerId)
        .orElseThrow(() -> new NotFoundException("Driver offer not found"));
    if (offer.status() != DriverOfferStatus.PENDING || !offer.expiresAt().isAfter(now)) {
      if (offer.status() == DriverOfferStatus.PENDING) {
        dispatch.respond(context.tenantId(), offer.id(), "PENDING", "EXPIRED", now);
      }
      throw new ConflictException("Driver offer is no longer pending");
    }
    Ride current = rides.find(context.tenantId(), offer.rideId())
        .orElseThrow(() -> new NotFoundException("Ride not found"));
    Ride assigned = current.assign(offer.driverId(), offer.vehicleId(), offer.shiftId(), now);
    try {
      if (!fleet.transitionShift(context.tenantId(), offer.shiftId(), ShiftStatus.AVAILABLE,
          ShiftStatus.RESERVED, now)
          || !rides.update(context.tenantId(), assigned, current.version())
          || !dispatch.respond(context.tenantId(), offer.id(), "PENDING", "ACCEPTED", now)) {
        throw new ConflictException("Offer lost a concurrent acceptance race");
      }
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Offer lost a concurrent acceptance race");
    }
    dispatch.expireSiblings(context.tenantId(), current.id(), offer.id(), now);
    dispatch.completeAttempt(context.tenantId(), offer.attemptId(), "ASSIGNED", now);
    rides.appendHistory(context.tenantId(), assigned.id(), current.status(), assigned.status(),
        context.accountId(), null, now);
    emit(context, assigned, "ride.driver_assigned", "dispatch.offer.accept");
    return assigned;
  }

  @Transactional
  public void reject(UUID offerId) {
    TenantContext context = requireAny(TenantRole.DRIVER);
    Instant now = clock.instant();
    DriverOffer offer = dispatch.lockOwnOffer(context.tenantId(), context.accountId(), offerId)
        .orElseThrow(() -> new NotFoundException("Driver offer not found"));
    if (offer.status() != DriverOfferStatus.PENDING || !offer.expiresAt().isAfter(now)
        || !dispatch.respond(context.tenantId(), offer.id(), "PENDING", "REJECTED", now)) {
      throw new ConflictException("Driver offer is no longer pending");
    }
    audit.record(context.tenantId(), context.accountId(), "dispatch.offer.reject", "driver_offer",
        offer.id(), "SUCCESS", json.createObjectNode());
  }

  private void emit(TenantContext context, Ride ride, String event, String action) {
    outbox.append(context.tenantId(), "ride", ride.id(), ride.version(), event, 1,
        json.valueToTree(new DispatchEvent(ride.id(), ride.status(), ride.driverId())), null);
    audit.record(context.tenantId(), context.accountId(), action, "ride", ride.id(), "SUCCESS",
        json.valueToTree(new DispatchAudit(ride.status())));
  }

  private TenantContext requireAny(TenantRole... roles) {
    TenantContext context = TenantContext.require();
    if (Set.of(roles).stream().noneMatch(context.roles()::contains)) {
      throw new TenantAccessDeniedException("Required tenant role is missing");
    }
    return context;
  }

  private record DispatchEvent(UUID rideId, RideStatus status, UUID driverId) {}

  private record DispatchAudit(RideStatus status) {}
}

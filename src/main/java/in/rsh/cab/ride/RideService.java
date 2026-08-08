package in.rsh.cab.ride;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.dispatch.internal.persistence.DispatchRepository;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.operations.IdempotencyReservation;
import in.rsh.cab.operations.IdempotencyService;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.payment.PaymentService;
import in.rsh.cab.pricing.FareQuote;
import in.rsh.cab.pricing.internal.persistence.PricingRepository;
import in.rsh.cab.ride.internal.persistence.RideRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RideService {

  private final RideRepository rides;
  private final PricingRepository pricing;
  private final DispatchRepository dispatch;
  private final FleetRepository fleet;
  private final IdempotencyService idempotency;
  private final OutboxService outbox;
  private final AuditService audit;
  private final PaymentService payments;
  private final ObjectMapper json;
  private final Clock clock;
  private final RideEventStream eventStream;

  public RideService(
      RideRepository rides, PricingRepository pricing, DispatchRepository dispatch, FleetRepository fleet,
      IdempotencyService idempotency, OutboxService outbox, AuditService audit, PaymentService payments,
      ObjectMapper json, Clock clock, RideEventStream eventStream) {
    this.rides = rides;
    this.pricing = pricing;
    this.dispatch = dispatch;
    this.fleet = fleet;
    this.idempotency = idempotency;
    this.outbox = outbox;
    this.audit = audit;
    this.payments = payments;
    this.json = json;
    this.clock = clock;
    this.eventStream = eventStream;
  }

  @Transactional
  public RideCreation create(String idempotencyKey, UUID quoteId) {
    TenantContext context = require(TenantRole.RIDER);
    IdempotencyReservation reservation = idempotency.reserve(
        context.tenantId(), context.accountId(), "ride.create", idempotencyKey, hash(quoteId.toString()));
    if (reservation.status() == IdempotencyReservation.Status.REPLAY) {
      return new RideCreation(json.treeToValue(reservation.safeResponse(), Ride.class), true);
    }
    Instant now = clock.instant();
    FareQuote quote = pricing.consumeQuote(context.tenantId(), context.accountId(), quoteId, now)
        .orElseThrow(() -> new ConflictException("Quote is missing, expired, or already consumed"));
    Ride ride = new Ride(UUID.randomUUID(), context.accountId(), quote.id(), quote.productId(),
        quote.pickup(), quote.dropoff(), quote.totalMinor(), quote.currency(), null, null, null,
        RideStatus.REQUESTED, null, null, now, null, null, null, null, null, now, 0);
    try {
      rides.insert(context.tenantId(), ride);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Rider already has an active ride");
    }
    rides.appendHistory(context.tenantId(), ride.id(), null, ride.status(), context.accountId(), null, now);
    emit(context, ride, "ride.created", "ride.create");
    idempotency.complete(context.tenantId(), context.accountId(), reservation.recordId(),
        "ride", ride.id(), 201, json.valueToTree(ride));
    return new RideCreation(ride, false);
  }

  @Transactional(readOnly = true)
  public Ride getOwn(UUID rideId) {
    TenantContext context = require(TenantRole.RIDER);
    return rides.findOwn(context.tenantId(), context.accountId(), rideId)
        .orElseThrow(() -> new NotFoundException("Ride not found"));
  }

  @Transactional(readOnly = true)
  public List<Ride> listOwn() {
    TenantContext context = require(TenantRole.RIDER);
    return rides.findOwn(context.tenantId(), context.accountId());
  }

  @Transactional
  public Ride cancelOwn(UUID rideId, long version, String reason) {
    TenantContext context = require(TenantRole.RIDER);
    Ride current = rides.findOwn(context.tenantId(), context.accountId(), rideId)
        .orElseThrow(() -> new NotFoundException("Ride not found"));
    return saveTransition(context, current, version,
        current.cancel(CancellationActor.RIDER, reason, clock.instant()), "ride.cancelled", reason);
  }

  @Transactional
  public Ride driverAction(UUID rideId, long version, DriverAction action, String reason) {
    TenantContext context = require(TenantRole.DRIVER);
    Ride current = rides.findAssignedToDriver(context.tenantId(), context.accountId(), rideId)
        .orElseThrow(() -> new NotFoundException("Assigned ride not found"));
    Instant now = clock.instant();
    Ride next = switch (action) {
      case ARRIVING -> current.arriving(now);
      case ARRIVE -> current.arrived(now);
      case START -> current.start(now);
      case COMPLETE -> current.complete(now);
      case CANCEL -> current.cancel(CancellationActor.DRIVER, reason, now);
    };
    String event = action == DriverAction.COMPLETE ? "ride.completed"
        : action == DriverAction.CANCEL ? "ride.cancelled" : "ride.status_changed";
    return saveTransition(context, current, version, next, event, reason);
  }

  @Transactional
  public Ride adminCancel(UUID rideId, long version, String reason) {
    TenantContext context = requireAny(TenantRole.TENANT_ADMIN, TenantRole.DISPATCHER);
    Ride current = rides.find(context.tenantId(), rideId)
        .orElseThrow(() -> new NotFoundException("Ride not found"));
    return saveTransition(context, current, version,
        current.cancel(CancellationActor.ADMIN, reason, clock.instant()), "ride.cancelled", reason);
  }

  public record RideCreation(Ride ride, boolean replayed) {}

  public enum DriverAction { ARRIVING, ARRIVE, START, COMPLETE, CANCEL }

  private Ride saveTransition(
      TenantContext context, Ride current, long expectedVersion, Ride next, String event,
      String reason) {
    if (current.version() != expectedVersion
        || !rides.update(context.tenantId(), next, expectedVersion)) {
      throw new ConflictException("Ride changed concurrently");
    }
    if ((next.status() == RideStatus.COMPLETED || next.status() == RideStatus.CANCELLED)
        && next.driverShiftId() != null) {
      ShiftStatus expected = next.status() == RideStatus.COMPLETED
          ? ShiftStatus.ON_TRIP : ShiftStatus.RESERVED;
      if (next.status() == RideStatus.CANCELLED && current.status() == RideStatus.IN_PROGRESS) {
        expected = ShiftStatus.ON_TRIP;
      }
      if (!fleet.transitionShift(
          context.tenantId(), next.driverShiftId(), expected, ShiftStatus.AVAILABLE,
          next.updatedAt())) {
        throw new ConflictException("Driver shift changed concurrently");
      }
    } else if (next.status() == RideStatus.IN_PROGRESS) {
      if (!fleet.transitionShift(context.tenantId(), next.driverShiftId(), ShiftStatus.RESERVED,
          ShiftStatus.ON_TRIP, next.updatedAt())) {
        throw new ConflictException("Driver shift changed concurrently");
      }
    }
    if (next.status() == RideStatus.CANCELLED) {
      dispatch.cancelRide(context.tenantId(), next.id(), next.updatedAt());
    }
    rides.appendHistory(context.tenantId(), next.id(), current.status(), next.status(),
        context.accountId(), reason, next.updatedAt());
    emit(context, next, event, event.replace('.', '_'));
    if (next.status() == RideStatus.COMPLETED) {
      payments.requestCapture(context.tenantId(), next);
    }
    return next;
  }

  private void emit(TenantContext context, Ride ride, String event, String action) {
    outbox.append(context.tenantId(), "ride", ride.id(), ride.version(), event, 1,
        json.valueToTree(new RideEvent(ride.id(), ride.status(), ride.driverId())), null);
    audit.record(context.tenantId(), context.accountId(), action, "ride", ride.id(), "SUCCESS",
        json.valueToTree(new RideAudit(ride.status())));
    eventStream.afterCommit(context.tenantId(), ride);
  }

  private TenantContext require(TenantRole role) {
    return requireAny(role);
  }

  private TenantContext requireAny(TenantRole... roles) {
    TenantContext context = TenantContext.require();
    if (Set.of(roles).stream().noneMatch(context.roles()::contains)) {
      throw new TenantAccessDeniedException("Required tenant role is missing");
    }
    return context;
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private record RideEvent(UUID rideId, RideStatus status, UUID driverId) {}

  private record RideAudit(RideStatus status) {}
}

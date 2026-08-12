package in.rsh.cab.payment;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.driver.internal.persistence.DriverProfileRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.operations.IdempotencyReservation;
import in.rsh.cab.operations.IdempotencyService;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import in.rsh.cab.ride.Ride;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

  private final PaymentRepository payments;
  private final DriverProfileRepository drivers;
  private final OutboxService outbox;
  private final AuditService audit;
  private final IdempotencyService idempotency;
  private final ObjectMapper json;
  private final Clock clock;
  private final String provider;
  private final String configReference;
  private final String secretReference;

  public PaymentService(
      PaymentRepository payments, DriverProfileRepository drivers, OutboxService outbox,
      AuditService audit, IdempotencyService idempotency, ObjectMapper json, Clock clock,
      @Value("${payments.provider}") String provider,
      @Value("${payments.config-reference}") String configReference,
      @Value("${payments.webhook-secret-reference}") String secretReference) {
    this.payments = payments;
    this.drivers = drivers;
    this.outbox = outbox;
    this.audit = audit;
    this.idempotency = idempotency;
    this.json = json;
    this.clock = clock;
    this.provider = provider;
    this.configReference = configReference;
    this.secretReference = secretReference;
  }

  @Transactional
  public void requestCapture(UUID tenantId, Ride ride) {
    Instant now = clock.instant();
    if (ride.fareMinor() == 0) {
      outbox.append(tenantId, "ride", ride.id(), ride.version(), "payment.not_required", 1,
          json.valueToTree(new PaymentNotRequired(ride.id(), ride.currency())), null);
      TenantContext context = TenantContext.require();
      audit.record(tenantId, context.accountId(), "payment.not_required", "ride", ride.id(),
          "SUCCESS", json.valueToTree(new PaymentNotRequired(ride.id(), ride.currency())));
      return;
    }
    PaymentAccount account = payments.activeAccount(
        tenantId, provider, configReference, secretReference, now);
    Payment payment = new Payment(UUID.randomUUID(), ride.id(), ride.riderAccountId(),
        ride.fareMinor(), ride.fareMinor(), 0, null, null, null,
        ride.currency(), PaymentState.CAPTURE_PENDING,
        null, 0, 0, null, now, now);
    payments.insertCaptureRequest(tenantId, account.id(), payment, UUID.randomUUID());
    Payment stored = payments.findByRide(tenantId, ride.riderAccountId(), ride.id()).orElseThrow();
    outbox.append(tenantId, "payment", stored.id(), stored.version(),
        "payment.capture_requested", 1,
        json.valueToTree(new OperationRequested(stored.id(), null)), null);
  }

  @Transactional(readOnly = true)
  public Payment getOwn(UUID paymentId) {
    TenantContext context = require(TenantRole.RIDER);
    return payments.findOwn(context.tenantId(), context.accountId(), paymentId)
        .orElseThrow(() -> new NotFoundException("Payment not found"));
  }

  @Transactional(readOnly = true)
  public Payment getOwnByRide(UUID rideId) {
    TenantContext context = require(TenantRole.RIDER);
    return payments.findByRide(context.tenantId(), context.accountId(), rideId)
        .orElseThrow(() -> new NotFoundException("Payment not found"));
  }

  @Transactional
  public RefundCreation refund(
      String idempotencyKey, UUID paymentId, long amountMinor, String reason) {
    TenantContext context = requireAny(TenantRole.FINANCE, TenantRole.TENANT_ADMIN);
    IdempotencyReservation reservation = idempotency.reserve(
        context.tenantId(), context.accountId(), "payment.refund",
        idempotencyKey, refundFingerprint(paymentId, amountMinor, reason));
    if (reservation.status() == IdempotencyReservation.Status.REPLAY) {
      return new RefundCreation(json.treeToValue(reservation.safeResponse(), Refund.class),
          reservation.httpStatus(), true);
    }
    Payment payment = payments.find(context.tenantId(), paymentId)
        .orElseThrow(() -> new NotFoundException("Payment not found"));
    if (payment.state() != PaymentState.CAPTURED) {
      throw new ConflictException("Only captured payments can be refunded");
    }
    long committed = payments.committedRefundMinor(context.tenantId(), paymentId);
    if (amountMinor <= 0 || committed > payment.capturedMinor()
        || amountMinor > payment.capturedMinor() - committed) {
      throw new ConflictException("Refund exceeds the unrefunded captured amount");
    }
    Instant now = clock.instant();
    Refund requested = new Refund(UUID.randomUUID(), paymentId, amountMinor, null, null, payment.currency(),
        reason, RefundState.PENDING, null, 0, 0, now, now);
    Refund refund;
    try {
      refund = payments.insertRefund(context.tenantId(), requested, context.accountId());
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Refund exceeds the unrefunded captured amount");
    }
    outbox.append(context.tenantId(), "refund", refund.id(), 0, "payment.refund_requested", 1,
        json.valueToTree(new OperationRequested(paymentId, refund.id())), null);
    audit.record(context.tenantId(), context.accountId(), "payment.refund_requested", "refund",
        refund.id(), "SUCCESS", json.valueToTree(new RefundAudit(paymentId, amountMinor,
            payment.currency())));
    idempotency.complete(context.tenantId(), context.accountId(), reservation.recordId(),
        "refund", refund.id(), 201, json.valueToTree(refund));
    return new RefundCreation(refund, 201, false);
  }

  @Transactional(readOnly = true)
  public Refund getRefund(UUID refundId) {
    TenantContext context = requireAny(TenantRole.FINANCE, TenantRole.TENANT_ADMIN);
    return payments.findRefund(context.tenantId(), refundId)
        .orElseThrow(() -> new NotFoundException("Refund not found"));
  }

  @Transactional(readOnly = true)
  public List<DriverEarning> ownEarnings() {
    TenantContext context = require(TenantRole.DRIVER);
    UUID driverId = drivers.findByTenantIdAndAccountId(context.tenantId(), context.accountId())
        .orElseThrow(() -> new NotFoundException("Driver profile not found")).id();
    return payments.earnings(context.tenantId(), driverId);
  }

  @Transactional
  public SettlementBatch settle(String currency) {
    TenantContext context = require(TenantRole.FINANCE);
    Instant now = clock.instant();
    PaymentAccount account = payments.activeAccount(
        context.tenantId(), provider, configReference, secretReference, now);
    SettlementBatch batch = payments.createSettlement(
        context.tenantId(), context.accountId(), account.id(), currency, now);
    for (SettlementBatch.Payout payout : batch.payouts()) {
      outbox.append(context.tenantId(), "payout", payout.id(), 0, "payout.requested", 1,
          json.valueToTree(new PayoutRequested(payout.id())), null);
    }
    audit.record(context.tenantId(), context.accountId(), "settlement.create", "settlement",
        batch.id(), "SUCCESS", json.valueToTree(new SettlementAudit(
            batch.currency(), batch.totalMinor())));
    return batch;
  }

  @Transactional(readOnly = true)
  public List<SettlementBatch> settlements() {
    TenantContext context = requireAny(TenantRole.FINANCE, TenantRole.TENANT_ADMIN);
    return payments.settlements(context.tenantId());
  }

  @Transactional
  public SettlementBatch.Payout releaseFailedPayout(UUID payoutId) {
    TenantContext context = require(TenantRole.FINANCE);
    payments.findPayout(context.tenantId(), payoutId)
        .orElseThrow(() -> new NotFoundException("Payout not found"));
    Instant now = clock.instant();
    if (!payments.releaseFailedPayout(context.tenantId(), payoutId, now)) {
      SettlementBatch.Payout payout = payments.findPayout(context.tenantId(), payoutId)
          .orElseThrow(() -> new NotFoundException("Payout not found"));
      if (payout.state() != PayoutState.RELEASED) {
        throw new ConflictException("Only failed payouts can be released");
      }
      return payout;
    }
    SettlementBatch.Payout payout = payments.findPayout(context.tenantId(), payoutId).orElseThrow();
    audit.record(context.tenantId(), context.accountId(), "payout.release_failed", "payout",
        payoutId, "SUCCESS", json.valueToTree(payout));
    return payout;
  }

  private TenantContext require(TenantRole role) {
    return requireAny(role);
  }

  private TenantContext requireAny(TenantRole... roles) {
    TenantContext context = TenantContext.require();
    if (Set.of(roles).stream().noneMatch(context.roles()::contains)) {
      throw new TenantAccessDeniedException("Required payment role is missing");
    }
    return context;
  }

  public record OperationRequested(UUID paymentId, UUID refundId) {}

  public record RefundCreation(Refund refund, int httpStatus, boolean replayed) {}

  private record RefundAudit(UUID paymentId, long amountMinor, String currency) {}

  private record SettlementAudit(String currency, long amountMinor) {}

  private record PaymentNotRequired(UUID rideId, String currency) {}

  private record PayoutRequested(UUID payoutId) {}

  private String refundFingerprint(UUID paymentId, long amountMinor, String reason) {
    String canonical = paymentId + "\n" + amountMinor + "\n" + reason;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}

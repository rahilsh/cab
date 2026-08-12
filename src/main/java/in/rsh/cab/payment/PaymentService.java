package in.rsh.cab.payment;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.driver.internal.persistence.DriverProfileRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import in.rsh.cab.ride.Ride;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
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
  private final ObjectMapper json;
  private final Clock clock;
  private final String provider;
  private final String configReference;
  private final String secretReference;

  public PaymentService(
      PaymentRepository payments, DriverProfileRepository drivers, OutboxService outbox,
      AuditService audit, ObjectMapper json, Clock clock,
      @Value("${payments.provider}") String provider,
      @Value("${payments.fake.config-reference}") String configReference,
      @Value("${payments.fake.webhook-secret-reference}") String secretReference) {
    this.payments = payments;
    this.drivers = drivers;
    this.outbox = outbox;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
    this.provider = provider;
    this.configReference = configReference;
    this.secretReference = secretReference;
  }

  @Transactional
  public void requestCapture(UUID tenantId, Ride ride) {
    Instant now = clock.instant();
    PaymentAccount account = payments.activeAccount(
        tenantId, provider, configReference, secretReference, now);
    Payment payment = new Payment(UUID.randomUUID(), ride.id(), ride.riderAccountId(),
        ride.fareMinor(), ride.fareMinor(), 0, ride.currency(), PaymentState.CAPTURE_PENDING,
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
  public Refund refund(UUID paymentId, long amountMinor, String reason) {
    TenantContext context = requireAny(TenantRole.FINANCE, TenantRole.TENANT_ADMIN);
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
    Refund refund = new Refund(UUID.randomUUID(), paymentId, amountMinor, payment.currency(),
        reason, RefundState.PENDING, null, 0, 0, now, now);
    try {
      payments.insertRefund(context.tenantId(), refund, context.accountId());
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Refund exceeds the unrefunded captured amount");
    }
    outbox.append(context.tenantId(), "refund", refund.id(), 0, "payment.refund_requested", 1,
        json.valueToTree(new OperationRequested(paymentId, refund.id())), null);
    audit.record(context.tenantId(), context.accountId(), "payment.refund_requested", "refund",
        refund.id(), "SUCCESS", json.valueToTree(new RefundAudit(paymentId, amountMinor,
            payment.currency())));
    return refund;
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
    SettlementBatch batch = payments.createSettlement(
        context.tenantId(), context.accountId(), currency, clock.instant());
    outbox.append(context.tenantId(), "settlement", batch.id(), 0, "settlement.completed", 1,
        json.valueToTree(new SettlementAudit(batch.currency(), batch.totalMinor())), null);
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

  private record RefundAudit(UUID paymentId, long amountMinor, String currency) {}

  private record SettlementAudit(String currency, long amountMinor) {}
}

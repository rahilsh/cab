package in.rsh.cab.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.driver.DriverProfile;
import in.rsh.cab.driver.DriverStatus;
import in.rsh.cab.driver.internal.persistence.DriverProfileRepository;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import in.rsh.cab.ride.Ride;
import in.rsh.cab.ride.RideStatus;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PaymentServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final PaymentRepository repository = mock(PaymentRepository.class);
  private final DriverProfileRepository drivers = mock(DriverProfileRepository.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final AuditService audit = mock(AuditService.class);
  private PaymentService service;

  @BeforeEach
  void setUp() {
    service = new PaymentService(repository, drivers, outbox, audit, new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC), "fake", "env:A", "env:S");
    context(TenantRole.RIDER);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void completionCreatesCaptureRequestAndRiderCanReadIt() {
    Ride ride = ride();
    PaymentAccount account = new PaymentAccount(
        UUID.randomUUID(), TENANT, "fake", "env:A", "env:S", true);
    Payment payment = payment(PaymentState.CAPTURE_PENDING);
    when(repository.activeAccount(TENANT, "fake", "env:A", "env:S", NOW)).thenReturn(account);
    when(repository.findByRide(TENANT, ride.riderAccountId(), ride.id()))
        .thenReturn(Optional.of(payment));

    service.requestCapture(TENANT, ride);
    assertEquals(payment, service.getOwnByRide(ride.id()));
    verify(repository).insertCaptureRequest(eq(TENANT), eq(account.id()), any(), any());

    when(repository.findOwn(TENANT, ACCOUNT, payment.id())).thenReturn(Optional.of(payment));
    assertEquals(payment, service.getOwn(payment.id()));
    assertThrows(NotFoundException.class, () -> service.getOwn(UUID.randomUUID()));
  }

  @Test
  void financeRefundEnforcesCaptureAndRemainingAmount() {
    context(TenantRole.FINANCE);
    Payment payment = payment(PaymentState.CAPTURED);
    when(repository.find(TENANT, payment.id())).thenReturn(Optional.of(payment));
    when(repository.committedRefundMinor(TENANT, payment.id())).thenReturn(40L);

    Refund refund = service.refund(payment.id(), 60, "adjustment");
    assertEquals(RefundState.PENDING, refund.state());
    verify(repository).insertRefund(TENANT, refund, ACCOUNT);
    when(repository.findRefund(TENANT, refund.id())).thenReturn(Optional.of(refund));
    assertEquals(refund, service.getRefund(refund.id()));
    assertThrows(ConflictException.class, () -> service.refund(payment.id(), 61, "too much"));

    Payment pending = payment(PaymentState.CAPTURE_PENDING);
    when(repository.find(TENANT, pending.id())).thenReturn(Optional.of(pending));
    assertThrows(ConflictException.class, () -> service.refund(pending.id(), 1, "early"));
    assertThrows(NotFoundException.class, () -> service.refund(UUID.randomUUID(), 1, "missing"));
  }

  @Test
  void driverReadsOwnEarningsAndFinanceCreatesSettlement() {
    context(TenantRole.DRIVER);
    UUID driverId = UUID.randomUUID();
    when(drivers.findByTenantIdAndAccountId(TENANT, ACCOUNT)).thenReturn(Optional.of(
        new DriverProfile(driverId, ACCOUNT, "Driver", null, DriverStatus.APPROVED, NOW, NOW)));
    when(repository.earnings(TENANT, driverId)).thenReturn(List.of(new DriverEarning("USD", 100)));
    assertEquals(100, service.ownEarnings().getFirst().availableMinor());

    context(TenantRole.FINANCE);
    SettlementBatch batch = new SettlementBatch(
        UUID.randomUUID(), "USD", "COMPLETED", 100, NOW, List.of());
    when(repository.createSettlement(TENANT, ACCOUNT, "USD", NOW)).thenReturn(batch);
    when(repository.settlements(TENANT)).thenReturn(List.of(batch));
    assertEquals(batch, service.settle("USD"));
    assertEquals(List.of(batch), service.settlements());

    context(TenantRole.RIDER);
    assertThrows(TenantAccessDeniedException.class, () -> service.settle("USD"));
    assertThrows(TenantAccessDeniedException.class, service::settlements);
  }

  private Payment payment(PaymentState state) {
    long captured = state == PaymentState.CAPTURED ? 100 : 0;
    return new Payment(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT, 100, 100, captured,
        "USD", state, state == PaymentState.CAPTURED ? "provider-pay" : null,
        1, 1, null, NOW, NOW);
  }

  private Ride ride() {
    return new Ride(UUID.randomUUID(), ACCOUNT, UUID.randomUUID(), UUID.randomUUID(),
        new GeoPoint(12.95, 77.6), new GeoPoint(13, 77.65), 100, "USD",
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), RideStatus.COMPLETED,
        null, null, NOW.minusSeconds(100), NOW.minusSeconds(90), NOW.minusSeconds(80),
        NOW.minusSeconds(70), NOW, null, NOW, 6);
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

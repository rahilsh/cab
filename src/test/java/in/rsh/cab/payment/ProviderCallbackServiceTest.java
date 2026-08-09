package in.rsh.cab.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import in.rsh.cab.tenancy.TenantExecution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProviderCallbackServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final PaymentRepository repository = mock(PaymentRepository.class);
  private final PaymentProvider provider = mock(PaymentProvider.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final ObjectMapper json = new ObjectMapper();
  private final PaymentAccount account = new PaymentAccount(
      UUID.randomUUID(), UUID.randomUUID(), "fake", "env:A", "env:S", true);
  private ProviderCallbackService service;

  @BeforeEach
  void setUp() {
    when(provider.name()).thenReturn("fake");
    TenantExecution tenantExecution = mock(TenantExecution.class);
    when(tenantExecution.inTransaction(any(), org.mockito.ArgumentMatchers.<java.util.function.Supplier<Object>>any()))
        .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    service = new ProviderCallbackService(repository, List.of(provider), outbox, json,
        Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5), 1500, tenantExecution);
    when(repository.findTenantForAccount(account.id())).thenReturn(Optional.of(account.tenantId()));
    when(repository.findAccount(account.id())).thenReturn(Optional.of(account));
    when(provider.verifies(any(), any(), any(), any())).thenReturn(true);
  }

  @Test
  void appliesCaptureOnceAndPostsLedger() throws Exception {
    UUID paymentId = UUID.randomUUID();
    String body = json.writeValueAsString(new ProviderEvent("evt-1",
        ProviderEvent.Type.CAPTURE_SUCCEEDED, paymentId, null, null, "provider-pay", 1,
        100L, "USD", null));
    when(repository.insertProviderEvent(any(), any(), any(), any())).thenReturn(true);
    when(repository.applyCaptureEvent(any(), any(), any(), any(Boolean.class), any(Integer.class)))
        .thenReturn(true);

    ProviderCallbackService.Result result = service.process(
        account.id(), "fake", NOW, "signature", body);

    assertTrue(result.accepted());
    assertTrue(result.applied());
    verify(repository).applyCaptureEvent(account, json.readValue(body, ProviderEvent.class),
        NOW, true, 1500);
    verify(repository).postCaptureLedger(account.tenantId(), paymentId, NOW);
  }

  @Test
  void rejectsReplayTimestampAndDeduplicatesProviderEvent() throws Exception {
    assertThrows(PaymentSignatureException.class, () -> service.process(
        account.id(), "fake", NOW.minusSeconds(301), "signature", "{}"));
    UUID paymentId = UUID.randomUUID();
    String body = json.writeValueAsString(new ProviderEvent("evt-1",
        ProviderEvent.Type.CAPTURE_FAILED, paymentId, null, null, "provider-pay", 1,
        100L, "USD", "DECLINED"));
    when(repository.insertProviderEvent(any(), any(), any(), any())).thenReturn(false);

    ProviderCallbackService.Result duplicate = service.process(
        account.id(), "fake", NOW, "signature", body);
    assertFalse(duplicate.accepted());
    assertFalse(duplicate.applied());
  }

  @Test
  void failedPayoutReleasesReservation() throws Exception {
    UUID payoutId = UUID.randomUUID();
    ProviderEvent event = new ProviderEvent("evt-payout", ProviderEvent.Type.PAYOUT_FAILED,
        null, null, payoutId, "provider-payout", 1, 100L, "USD", "ACCOUNT_CLOSED");
    String body = json.writeValueAsString(event);
    when(repository.insertProviderEvent(any(), any(), any(), any())).thenReturn(true);
    when(repository.applyPayoutEvent(account, event, NOW, false)).thenReturn(true);

    ProviderCallbackService.Result result = service.process(
        account.id(), "fake", NOW, "signature", body);

    assertTrue(result.applied());
    verify(repository).postPayoutReleaseLedger(account.tenantId(), payoutId, NOW);
  }
}

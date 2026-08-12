package in.rsh.cab.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class PaymentOperationWorkerTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final PaymentRepository repository = mock(PaymentRepository.class);
  private final PaymentProvider provider = mock(PaymentProvider.class);
  private PaymentOperationWorker worker;

  @BeforeEach
  void setUp() {
    when(provider.name()).thenReturn("fake");
    worker = new PaymentOperationWorker(repository, List.of(provider),
        new TransactionTemplate(new TestTransactionManager()),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void submitsCaptureAfterClaimingDurableAttempt() {
    Payment payment = payment();
    PaymentAccount account = account();
    when(repository.find(TENANT, payment.id())).thenReturn(Optional.of(payment));
    when(repository.markCaptureProcessing(TENANT, payment.id())).thenReturn(true);
    when(repository.findAccountForPayment(TENANT, payment.id())).thenReturn(Optional.of(account));
    when(provider.capture(any(), any(), any(Long.class), any(), any()))
        .thenReturn(new PaymentProvider.Submission("provider-pay", "provider-request"));

    assertTrue(worker.process(event("payment.capture_requested", payment.id())));
    verify(repository).markCaptureSubmitted(
        TENANT, payment.id(), "provider-pay", "provider-request");
    assertFalse(worker.process(event("other", payment.id())));
  }

  @Test
  void recordsSafeFailureCodeWhenProviderCallFails() {
    Payment payment = payment();
    when(repository.find(TENANT, payment.id())).thenReturn(Optional.of(payment));
    when(repository.markCaptureProcessing(TENANT, payment.id())).thenReturn(true);
    when(repository.findAccountForPayment(TENANT, payment.id())).thenReturn(Optional.of(account()));
    when(provider.capture(any(), any(), any(Long.class), any(), any()))
        .thenThrow(new IllegalStateException("secret provider detail"));

    assertThrows(IllegalStateException.class,
        () -> worker.process(event("payment.capture_requested", payment.id())));
    verify(repository).markCaptureSubmissionFailed(
        TENANT, payment.id(), "PROVIDER_UNAVAILABLE", NOW);
  }

  private Payment payment() {
    return new Payment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, 100, 0,
        "USD", PaymentState.CAPTURE_PENDING, null, 0, 0, null, NOW, NOW);
  }

  private PaymentAccount account() {
    return new PaymentAccount(UUID.randomUUID(), TENANT, "fake", "env:A", "env:S", true);
  }

  private OutboxEvent event(String type, UUID id) {
    return new OutboxEvent(UUID.randomUUID(), TENANT, "payment", id, 0, type, 1,
        new ObjectMapper().createObjectNode(), NOW, null, null, 1);
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}

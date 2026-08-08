package in.rsh.cab.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.notification.internal.persistence.NotificationRepository;
import in.rsh.cab.notification.internal.persistence.NotificationRepository.Delivery;
import in.rsh.cab.operations.InboxService;
import in.rsh.cab.operations.OutboxEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class NotificationDeliveryWorkerTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID RECIPIENT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final NotificationRepository repository = mock(NotificationRepository.class);
  private final InboxService inbox = mock(InboxService.class);
  private final NotificationProvider provider = mock(NotificationProvider.class);
  private NotificationDeliveryWorker worker;

  @BeforeEach
  void setUp() {
    when(provider.channel()).thenReturn("LOCAL");
    worker = new NotificationDeliveryWorker(repository, inbox, List.of(provider),
        new TransactionTemplate(new TestTransactionManager()), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void deduplicatesAndRespectsOrdinaryPreferences() {
    OutboxEvent event = event("rating.created");
    when(repository.getOrCreateDelivery(any(), eq(TENANT), eq(RECIPIENT), eq(event.id()),
        eq(event.eventType()), eq("LOCAL"), eq("rating"), eq(1), eq("PENDING"), eq(NOW)))
        .thenReturn(new Delivery(UUID.randomUUID(), "DELIVERED", 1));
    assertFalse(worker.process(event, RECIPIENT, "LOCAL", "rating", 1, "body"));

    when(repository.preferenceEnabled(TENANT, RECIPIENT, event.eventType(), "LOCAL"))
        .thenReturn(false);
    when(repository.getOrCreateDelivery(any(), eq(TENANT), eq(RECIPIENT), eq(event.id()),
        eq(event.eventType()), eq("LOCAL"), eq("rating"), eq(1), eq("SKIPPED"), eq(NOW)))
        .thenReturn(new Delivery(UUID.randomUUID(), "SKIPPED", 0));
    assertFalse(worker.process(event, RECIPIENT, "LOCAL", "rating", 1, "body"));
    verify(provider, never()).send(any());
  }

  @Test
  void safetyBypassesPreferenceAndRecordsProviderResult() {
    OutboxEvent event = event("safety.incident_reported");
    UUID deliveryId = UUID.randomUUID();
    when(repository.getOrCreateDelivery(any(), eq(TENANT), eq(RECIPIENT), eq(event.id()),
        eq(event.eventType()), eq("LOCAL"), eq("safety"), eq(1), eq("PENDING"), eq(NOW)))
        .thenReturn(new Delivery(deliveryId, "PENDING", 0));
    when(repository.claimDelivery(TENANT, deliveryId)).thenReturn(true);
    when(provider.send(any())).thenReturn("provider-id");
    assertTrue(worker.process(event, RECIPIENT, "LOCAL", "safety", 1, "body"));
    verify(repository, never()).preferenceEnabled(any(), any(), any(), any());
    verify(repository).insertAttempt(eq(TENANT), org.mockito.ArgumentMatchers.any(), eq(1),
        eq("SUCCEEDED"), eq("provider-id"), eq(null), eq(NOW));
  }

  @Test
  void recordsSafeFailureWithoutLeakingProviderDetails() {
    OutboxEvent event = event("ride.completed");
    when(repository.preferenceEnabled(TENANT, RECIPIENT, event.eventType(), "LOCAL"))
        .thenReturn(true);
    UUID deliveryId = UUID.randomUUID();
    when(repository.getOrCreateDelivery(any(), eq(TENANT), eq(RECIPIENT), eq(event.id()),
        eq(event.eventType()), eq("LOCAL"), eq("ride"), eq(1), eq("PENDING"), eq(NOW)))
        .thenReturn(new Delivery(deliveryId, "FAILED", 1));
    when(repository.claimDelivery(TENANT, deliveryId)).thenReturn(true);
    when(provider.send(any())).thenThrow(new IllegalStateException("secret detail"));
    assertThrows(IllegalStateException.class,
        () -> worker.process(event, RECIPIENT, "LOCAL", "ride", 1, "body"));
    verify(repository).insertAttempt(eq(TENANT), org.mockito.ArgumentMatchers.any(), eq(2),
        eq("FAILED"), eq(null), eq("PROVIDER_UNAVAILABLE"), eq(NOW));
  }

  private OutboxEvent event(String type) {
    return new OutboxEvent(UUID.randomUUID(), TENANT, "event", UUID.randomUUID(), 1, type, 1,
        new ObjectMapper().createObjectNode(), NOW, null, null, 1);
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) {}
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}

package in.rsh.cab.operations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.notification.NotificationDeliveryWorker;
import in.rsh.cab.notification.NotificationRecipientResolver;
import in.rsh.cab.payment.PaymentOperationWorker;
import in.rsh.cab.ride.RideStreamRedisPublisher;
import in.rsh.cab.tenancy.TenantExecution;
import in.rsh.cab.webhook.WebhookDeliveryWorker;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OutboxConsumerTest {

  @Test
  void paymentConsumerPreservesHandledResult() {
    PaymentOperationWorker worker = mock(PaymentOperationWorker.class);
    OutboxEvent event = event("payment.capture_requested");
    when(worker.process(event)).thenReturn(true);
    assertTrue(new PaymentOutboxConsumer(worker).process(event));
  }

  @Test
  void webhookConsumerIgnoresUnallowedEventsAndProcessesAllowedEvents() {
    WebhookDeliveryWorker worker = mock(WebhookDeliveryWorker.class);
    WebhookOutboxConsumer consumer = new WebhookOutboxConsumer(worker);
    assertFalse(consumer.process(event("payment.captured")));
    verify(worker, never()).process(any());
    OutboxEvent allowed = event("ride.completed");
    assertTrue(consumer.process(allowed));
    verify(worker).process(allowed);
  }

  @Test
  void notificationConsumerRoutesEveryResolvedRecipient() {
    NotificationRecipientResolver resolver = mock(NotificationRecipientResolver.class);
    NotificationDeliveryWorker worker = mock(NotificationDeliveryWorker.class);
    TenantExecution execution = mock(TenantExecution.class);
    when(execution.inTransaction(any(), org.mockito.ArgumentMatchers
        .<java.util.function.Supplier<List<UUID>>>any())).thenAnswer(invocation ->
            ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    OutboxEvent event = event("ride.completed");
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    when(resolver.resolve(event)).thenReturn(List.of(first, second));
    NotificationOutboxConsumer consumer = new NotificationOutboxConsumer(resolver, worker, execution);
    assertTrue(consumer.process(event));
    verify(worker).process(event, first, "LOCAL", "ride_completed", 1, "ride.completed");
    verify(worker).process(event, second, "LOCAL", "ride_completed", 1, "ride.completed");
  }

  @Test
  void safetyNotificationUsesGenericBody() {
    NotificationRecipientResolver resolver = mock(NotificationRecipientResolver.class);
    NotificationDeliveryWorker worker = mock(NotificationDeliveryWorker.class);
    TenantExecution execution = mock(TenantExecution.class);
    when(execution.inTransaction(any(), org.mockito.ArgumentMatchers
        .<java.util.function.Supplier<List<UUID>>>any())).thenAnswer(invocation ->
            ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    OutboxEvent event = event("safety.incident_reported");
    UUID recipient = UUID.randomUUID();
    when(resolver.resolve(event)).thenReturn(List.of(recipient));

    assertTrue(new NotificationOutboxConsumer(resolver, worker, execution).process(event));

    verify(worker).process(event, recipient, "LOCAL", "safety_incident_reported", 1,
        "A safety incident has an update");
  }

  @Test
  void rideStreamConsumerPublishesAggregateVersion() {
    RideStreamRedisPublisher publisher = mock(RideStreamRedisPublisher.class);
    var payload = new ObjectMapper().createObjectNode().put("status", "COMPLETED");
    OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "ride",
        UUID.randomUUID(), 4, "ride.completed", 1, payload,
        Instant.parse("2026-08-09T10:00:00Z"), null, null, 1, UUID.randomUUID());
    assertTrue(new RideStreamOutboxConsumer(publisher).process(event));
    verify(publisher).publish(org.mockito.ArgumentMatchers.eq(event.tenantId()),
        org.mockito.ArgumentMatchers.argThat(message -> message.eventId().equals(event.id())
            && message.rideId().equals(event.aggregateId())
            && message.version() == event.aggregateVersion()));
  }

  private OutboxEvent event(String type) {
    return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "ride", UUID.randomUUID(), 1,
        type, 1, new ObjectMapper().createObjectNode(), Instant.parse("2026-08-09T10:00:00Z"),
        null, null, 1, UUID.randomUUID());
  }
}

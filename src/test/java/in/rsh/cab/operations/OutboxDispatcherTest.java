package in.rsh.cab.operations;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.notification.NotificationDeliveryWorker;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import in.rsh.cab.webhook.WebhookDeliveryWorker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OutboxDispatcherTest {

  private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");
  private final OutboxPoller poller = mock(OutboxPoller.class);
  private final TenantRepository tenants = mock(TenantRepository.class);
  private final NotificationDeliveryWorker notifications = mock(NotificationDeliveryWorker.class);
  private final WebhookDeliveryWorker webhooks = mock(WebhookDeliveryWorker.class);
  private final OutboxConsumer first = mock(OutboxConsumer.class);
  private final OutboxConsumer second = mock(OutboxConsumer.class);
  private final OutboxDispatcher dispatcher = new OutboxDispatcher(poller,
      tenants, List.of(first, second), notifications, webhooks, Clock.fixed(NOW, ZoneOffset.UTC), 10, 10, 3,
      Duration.ofMinutes(1), Duration.ofSeconds(2), Duration.ofMinutes(5));

  @Test
  void acknowledgesOnlyAfterAllConsumersRun() {
    OutboxEvent event = event(1);
    dispatcher.process(event);
    verify(first).process(event);
    verify(second).process(event);
    verify(poller).published(event);
  }

  @Test
  void retriesPartialFanoutWithBackoffAndEventuallyFails() {
    OutboxEvent retry = event(2);
    doThrow(new IllegalStateException("temporary")).when(second).process(retry);
    dispatcher.process(retry);
    verify(poller).retry(retry, NOW.plusSeconds(4), "temporary");

    OutboxEvent failed = event(3);
    doThrow(new IllegalStateException("still down")).when(first).process(failed);
    dispatcher.process(failed);
    verify(poller).failed(failed, "still down");
  }

  @Test
  void scheduledCyclePollsEachActiveTenantAndRunsDeliveryRecovery() {
    UUID tenant = UUID.randomUUID();
    OutboxEvent event = event(1);
    when(tenants.findActiveIds()).thenReturn(List.of(tenant));
    when(poller.lease(tenant, 10, Duration.ofMinutes(1))).thenReturn(List.of(event));
    dispatcher.dispatch();
    verify(notifications).retryDue(tenant, 10);
    verify(webhooks).retryDue(tenant, 10);
    verify(poller).published(event);
  }

  private OutboxEvent event(int attempts) {
    return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "ride", UUID.randomUUID(), 1,
        "ride.completed", 1, new ObjectMapper().createObjectNode(), NOW, null, null, attempts,
        UUID.randomUUID());
  }
}

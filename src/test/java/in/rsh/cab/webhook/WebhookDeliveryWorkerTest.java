package in.rsh.cab.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.tenancy.TenantExecution;
import in.rsh.cab.webhook.internal.persistence.WebhookRepository;
import in.rsh.cab.webhook.internal.persistence.WebhookRepository.Delivery;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WebhookDeliveryWorkerTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final WebhookRepository repository = mock(WebhookRepository.class);
  private final WebhookTransport transport = mock(WebhookTransport.class);
  private final SecretResolver secrets = reference -> "secret";
  private final WebhookSecurity security = new WebhookSecurity(host -> List.of(
      InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34})));
  private final TenantExecution tenantExecution = mock(TenantExecution.class);
  private WebhookDeliveryWorker worker;

  @BeforeEach
  void setUp() {
    when(tenantExecution.inTransaction(any(), org.mockito.ArgumentMatchers.<java.util.function.Supplier<Object>>any()))
        .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    org.mockito.Mockito.doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(1)).run();
      return null;
    }).when(tenantExecution).inTransaction(any(), any(Runnable.class));
    worker = new WebhookDeliveryWorker(repository, security, transport, secrets,
        new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(10), 6,
        Duration.ofSeconds(30), tenantExecution);
  }

  @Test
  void signsImmutableEnvelopeAndSendsThroughInjectedTransport() {
    OutboxEvent event = event("ride.completed");
    WebhookSubscription subscription = subscription();
    when(repository.matching(TENANT, event.eventType())).thenReturn(List.of(subscription));
    final String[] envelope = new String[1];
    when(repository.createDelivery(eq(subscription), eq(TENANT), eq(event), any(), eq(NOW), eq(NOW)))
        .thenAnswer(invocation -> {
          envelope[0] = invocation.getArgument(3);
          return Optional.of(delivery(subscription, event.id(), envelope[0], 0));
        });
    when(repository.claimDue(eq(TENANT), eq(200), eq(NOW), eq(NOW.plusSeconds(30)), any()))
        .thenAnswer(invocation -> List.of(delivery(subscription, event.id(), envelope[0], 0)));
    when(transport.post(any(), any(), any(), eq(Duration.ofSeconds(10))))
        .thenAnswer(invocation -> {
          Map<String, String> headers = invocation.getArgument(2);
          assertEquals(event.id().toString(), headers.get("X-Cab-Event-ID"));
          assertEquals(Long.toString(NOW.getEpochSecond()), headers.get("X-Cab-Signature-Timestamp"));
          assertTrue(headers.get("X-Cab-Signature").matches("v1=[0-9a-f]{64}"));
          assertTrue(((String) invocation.getArgument(1)).contains("\"eventType\":\"ride.completed\""));
          return new WebhookTransport.Response(204);
        });

    assertEquals(1, worker.process(event));
    verify(repository).complete(eq(TENANT), any(), any(), eq(1), eq(204), eq(NOW));
  }

  @Test
  void rejectsSensitiveEventsAndRetriesFailuresExponentially() {
    assertEquals(0, worker.process(event("payment.captured")));
    verify(repository, never()).matching(any(), any());

    Delivery delivery = new Delivery(UUID.randomUUID(), TENANT, subscription(), UUID.randomUUID(),
        "ride.completed", 1, "{}", NOW, 2, UUID.randomUUID());
    when(transport.post(any(), any(), any(), any())).thenReturn(new WebhookTransport.Response(503));
    assertFalse(worker.deliver(delivery));
    verify(repository).retry(TENANT, delivery.id(), delivery.leaseToken(), 3, 503, "HTTP_STATUS",
        NOW.plusSeconds(8), false, NOW);
  }

  @Test
  void dnsIsResolvedAgainImmediatelyBeforeEveryDelivery() throws Exception {
    WebhookSecurity rebinding = new WebhookSecurity(host -> List.of(InetAddress.getByName("127.0.0.1")));
    WebhookDeliveryWorker blocked = new WebhookDeliveryWorker(repository, rebinding, transport,
        secrets, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(10), 6,
        Duration.ofSeconds(30), tenantExecution);
    Delivery delivery = new Delivery(UUID.randomUUID(), TENANT, subscription(), UUID.randomUUID(),
        "ride.completed", 1, "{}", NOW, 0, UUID.randomUUID());
    assertFalse(blocked.deliver(delivery));
    verify(transport, never()).post(any(), any(), any(), any());
    verify(repository).retry(TENANT, delivery.id(), delivery.leaseToken(), 1, null, "DELIVERY_FAILED",
        NOW.plusSeconds(2), false, NOW);
  }

  @Test
  void processesBoundedDueRetries() {
    Delivery delivery = new Delivery(UUID.randomUUID(), TENANT, subscription(), UUID.randomUUID(),
        "ride.completed", 1, "{}", NOW, 1, UUID.randomUUID());
    when(repository.claimDue(eq(TENANT), eq(10), eq(NOW), eq(NOW.plusSeconds(30)), any()))
        .thenReturn(List.of(delivery));
    when(transport.post(any(), any(), any(), any())).thenReturn(new WebhookTransport.Response(200));
    assertEquals(1, worker.retryDue(TENANT, 10));
    assertThrows(IllegalArgumentException.class, () -> worker.retryDue(TENANT, 0));
  }

  private WebhookSubscription subscription() {
    return new WebhookSubscription(UUID.randomUUID(), "https://example.com/hook", "env:SECRET",
        Set.of("ride.completed"), true, NOW, NOW, 0);
  }

  private OutboxEvent event(String type) {
    return new OutboxEvent(UUID.randomUUID(), TENANT, "ride", UUID.randomUUID(), 6, type, 1,
        new ObjectMapper().createObjectNode(), NOW, null, null, 1, UUID.randomUUID());
  }

  private Delivery delivery(
      WebhookSubscription subscription, UUID eventId, String payload, int attempts) {
    return new Delivery(UUID.randomUUID(), TENANT, subscription, eventId, "ride.completed", 1,
        payload, NOW, attempts, UUID.randomUUID());
  }
}

package in.rsh.cab.notification;

import in.rsh.cab.notification.internal.persistence.NotificationRepository;
import in.rsh.cab.notification.internal.persistence.NotificationRepository.Delivery;
import in.rsh.cab.operations.InboxService;
import in.rsh.cab.operations.OutboxEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationDeliveryWorker {

  private final NotificationRepository notifications;
  private final InboxService inbox;
  private final Map<String, NotificationProvider> providers;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public NotificationDeliveryWorker(
      NotificationRepository notifications, InboxService inbox, List<NotificationProvider> providers,
      TransactionTemplate transactions, Clock clock) {
    this.notifications = notifications;
    this.inbox = inbox;
    this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
        NotificationProvider::channel, Function.identity()));
    this.transactions = transactions;
    this.clock = clock;
  }

  public boolean process(OutboxEvent event, UUID recipientId, String channel,
      String templateKey, int templateVersion, String body) {
    String consumer = "notification:" + recipientId + ":" + channel + ":" + templateKey
        + ":" + templateVersion;
    Instant now = clock.instant();
    boolean bypass = event.eventType().startsWith("safety.")
        || event.eventType().startsWith("payment.")
        || event.eventType().equals("ride.cancelled");
    boolean enabled = bypass || notifications.preferenceEnabled(
        event.tenantId(), recipientId, event.eventType(), channel);
    Delivery delivery = notifications.getOrCreateDelivery(UUID.randomUUID(), event.tenantId(),
        recipientId, event.id(),
        event.eventType(), channel, templateKey, templateVersion,
        enabled ? "PENDING" : "SKIPPED", now);
    if (!enabled) {
      inbox.receive(event.tenantId(), consumer, event.id());
      return false;
    }
    if (!notifications.claimDelivery(event.tenantId(), delivery.id())) {
      return false;
    }
    UUID deliveryId = delivery.id();
    int attempt = delivery.attempts() + 1;
    NotificationProvider provider = providers.get(channel);
    if (provider == null) {
      throw new IllegalStateException("Notification provider is not configured: " + channel);
    }
    try {
      String providerId = provider.send(new NotificationProvider.Message(deliveryId,
          event.tenantId(), recipientId, event.eventType(), templateKey, templateVersion, body));
      transactions.executeWithoutResult(status -> {
        notifications.insertAttempt(event.tenantId(), deliveryId, attempt, "SUCCEEDED", providerId,
            null, clock.instant());
        notifications.markDelivery(event.tenantId(), deliveryId, "DELIVERED", clock.instant());
        inbox.receive(event.tenantId(), consumer, event.id());
      });
      return true;
    } catch (RuntimeException exception) {
      transactions.executeWithoutResult(status -> {
        notifications.insertAttempt(event.tenantId(), deliveryId, attempt, "FAILED", null,
            "PROVIDER_UNAVAILABLE", clock.instant());
        notifications.markDelivery(event.tenantId(), deliveryId, "FAILED", null);
      });
      throw exception;
    }
  }
}

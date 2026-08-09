package in.rsh.cab.notification;

import in.rsh.cab.notification.internal.persistence.NotificationRepository;
import in.rsh.cab.notification.internal.persistence.NotificationRepository.Delivery;
import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.tenancy.TenantExecution;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryWorker {

  private final NotificationRepository notifications;
  private final Map<String, NotificationProvider> providers;
  private final TenantExecution tenantExecution;
  private final Clock clock;
  private final java.time.Duration leaseDuration;
  private final int maxAttempts;

  public NotificationDeliveryWorker(
      NotificationRepository notifications, List<NotificationProvider> providers,
      TenantExecution tenantExecution, Clock clock,
      @org.springframework.beans.factory.annotation.Value("${notifications.lease-duration:PT30S}")
      java.time.Duration leaseDuration,
      @org.springframework.beans.factory.annotation.Value("${notifications.max-attempts:6}")
      int maxAttempts) {
    this.notifications = notifications;
    this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
        NotificationProvider::channel, Function.identity()));
    this.tenantExecution = tenantExecution;
    this.clock = clock;
    this.leaseDuration = leaseDuration;
    this.maxAttempts = maxAttempts;
  }

  public boolean process(OutboxEvent event, UUID recipientId, String channel,
      String templateKey, int templateVersion, String body) {
    Instant now = clock.instant();
    boolean bypass = event.eventType().startsWith("safety.")
        || event.eventType().startsWith("payment.")
        || event.eventType().equals("ride.cancelled");
    boolean enabled = bypass || tenantExecution.inTransaction(event.tenantId(),
        () -> notifications.preferenceEnabled(
            event.tenantId(), recipientId, event.eventType(), channel));
    Delivery delivery = tenantExecution.inTransaction(event.tenantId(),
        () -> notifications.getOrCreateDelivery(UUID.randomUUID(), event.tenantId(),
            recipientId, event.id(), event.eventType(), channel, templateKey, templateVersion,
            body, enabled ? "PENDING" : "SKIPPED", now));
    if (!enabled) {
      return false;
    }
    UUID leaseToken = UUID.randomUUID();
    Delivery claimed = tenantExecution.inTransaction(event.tenantId(),
        () -> notifications.claimDelivery(event.tenantId(), delivery.id(), now,
            now.plus(leaseDuration), leaseToken).orElse(null));
    if (claimed == null) {
      return false;
    }
    return deliver(claimed);
  }

  public int retryDue(UUID tenantId, int limit) {
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("Notification retry limit must be between 1 and 200");
    }
    Instant now = clock.instant();
    List<Delivery> due = tenantExecution.inTransaction(tenantId,
        () -> notifications.claimDue(tenantId, limit, now, now.plus(leaseDuration),
            UUID.randomUUID()));
    int delivered = 0;
    for (Delivery delivery : due) {
      if (deliver(delivery)) {
        delivered++;
      }
    }
    return delivered;
  }

  private boolean deliver(Delivery delivery) {
    UUID deliveryId = delivery.id();
    int attempt = delivery.attempts() + 1;
    String channel = delivery.channel();
    NotificationProvider provider = providers.get(channel);
    if (provider == null) {
      throw new IllegalStateException("Notification provider is not configured: " + channel);
    }
    try {
      String providerId = provider.send(new NotificationProvider.Message(deliveryId,
          delivery.tenantId(), delivery.recipientId(), delivery.eventType(), delivery.templateKey(),
          delivery.templateVersion(), delivery.body()));
      tenantExecution.inTransaction(delivery.tenantId(), () -> notifications.complete(
          delivery.tenantId(), deliveryId, delivery.leaseToken(), attempt, providerId,
          clock.instant()));
      return true;
    } catch (RuntimeException exception) {
      Instant now = clock.instant();
      boolean failed = attempt >= maxAttempts;
      long delaySeconds = Math.min(3600, 1L << Math.min(attempt, 12));
      tenantExecution.inTransaction(delivery.tenantId(), () -> notifications.retry(
          delivery.tenantId(), deliveryId, delivery.leaseToken(), attempt, "PROVIDER_UNAVAILABLE",
          now.plusSeconds(delaySeconds), failed, now));
      return false;
    }
  }
}

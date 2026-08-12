package in.rsh.cab.operations;

import in.rsh.cab.notification.NotificationDeliveryWorker;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import in.rsh.cab.webhook.WebhookDeliveryWorker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "outbox.dispatcher.enabled", matchIfMissing = true)
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
  private final OutboxPoller poller;
  private final TenantRepository tenants;
  private final List<OutboxConsumer> consumers;
  private final NotificationDeliveryWorker notifications;
  private final WebhookDeliveryWorker webhooks;
  private final Clock clock;
  private final int batchSize;
  private final int deliveryBatchSize;
  private final int maxAttempts;
  private final Duration leaseDuration;
  private final Duration initialBackoff;
  private final Duration maxBackoff;

  public OutboxDispatcher(
      OutboxPoller poller, TenantRepository tenants, List<OutboxConsumer> consumers,
      NotificationDeliveryWorker notifications, WebhookDeliveryWorker webhooks, Clock clock,
      @Value("${outbox.dispatcher.batch-size:50}") int batchSize,
      @Value("${outbox.dispatcher.delivery-batch-size:50}") int deliveryBatchSize,
      @Value("${outbox.dispatcher.max-attempts:10}") int maxAttempts,
      @Value("${outbox.dispatcher.lease-duration:PT1M}") Duration leaseDuration,
      @Value("${outbox.dispatcher.initial-backoff:PT2S}") Duration initialBackoff,
      @Value("${outbox.dispatcher.max-backoff:PT5M}") Duration maxBackoff) {
    this.poller = poller;
    this.tenants = tenants;
    this.consumers = consumers;
    this.notifications = notifications;
    this.webhooks = webhooks;
    this.clock = clock;
    this.batchSize = batchSize;
    this.deliveryBatchSize = deliveryBatchSize;
    this.maxAttempts = maxAttempts;
    this.leaseDuration = leaseDuration;
    this.initialBackoff = initialBackoff;
    this.maxBackoff = maxBackoff;
  }

  @Scheduled(fixedDelayString = "${outbox.dispatcher.fixed-delay:1000}")
  public void dispatch() {
    for (UUID tenantId : tenants.findActiveIds()) {
      try {
        recoverDeliveries(tenantId);
        for (OutboxEvent event : poller.lease(tenantId, batchSize, leaseDuration)) {
          process(event);
        }
      } catch (RuntimeException exception) {
        log.warn("Outbox dispatch cycle failed for tenant={}", tenantId, exception);
      }
    }
  }

  private void recoverDeliveries(UUID tenantId) {
    try {
      notifications.retryDue(tenantId, deliveryBatchSize);
    } catch (RuntimeException exception) {
      log.warn("Notification recovery failed for tenant={}", tenantId, exception);
    }
    try {
      webhooks.retryDue(tenantId, deliveryBatchSize);
    } catch (RuntimeException exception) {
      log.warn("Webhook recovery failed for tenant={}", tenantId, exception);
    }
  }

  void process(OutboxEvent event) {
    try {
      for (OutboxConsumer consumer : consumers) {
        consumer.process(event);
      }
      poller.published(event);
    } catch (RuntimeException exception) {
      try {
        if (event.attempts() >= maxAttempts) {
          poller.failed(event, exception.getMessage());
        } else {
          poller.retry(event, clock.instant().plus(backoff(event.attempts())),
              exception.getMessage());
        }
      } catch (IllegalStateException leaseLost) {
        log.info("Outbox lease lost before failure was recorded event={}", event.id());
      }
    }
  }

  private Duration backoff(int attempts) {
    long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 20);
    Duration delay;
    try {
      delay = initialBackoff.multipliedBy(multiplier);
    } catch (ArithmeticException exception) {
      delay = maxBackoff;
    }
    return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
  }
}

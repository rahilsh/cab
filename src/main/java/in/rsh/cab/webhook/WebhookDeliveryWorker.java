package in.rsh.cab.webhook;

import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.tenancy.TenantExecution;
import in.rsh.cab.webhook.internal.persistence.WebhookRepository;
import in.rsh.cab.webhook.internal.persistence.WebhookRepository.Delivery;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookDeliveryWorker {

  private final WebhookRepository webhooks;
  private final WebhookSecurity security;
  private final WebhookTransport transport;
  private final SecretResolver secrets;
  private final ObjectMapper json;
  private final Clock clock;
  private final Duration requestTimeout;
  private final int maxAttempts;
  private final Duration leaseDuration;
  private final TenantExecution tenantExecution;

  public WebhookDeliveryWorker(
      WebhookRepository webhooks, WebhookSecurity security, WebhookTransport transport,
      SecretResolver secrets, ObjectMapper json, Clock clock,
      @Value("${webhooks.request-timeout:PT10S}") Duration requestTimeout,
      @Value("${webhooks.max-attempts:6}") int maxAttempts,
      @Value("${webhooks.lease-duration:PT30S}") Duration leaseDuration,
      TenantExecution tenantExecution) {
    this.webhooks = webhooks;
    this.security = security;
    this.transport = transport;
    this.secrets = secrets;
    this.json = json;
    this.clock = clock;
    this.requestTimeout = requestTimeout;
    this.maxAttempts = maxAttempts;
    this.leaseDuration = leaseDuration;
    this.tenantExecution = tenantExecution;
  }

  public int process(OutboxEvent event) {
    if (!WebhookService.EVENT_ALLOWLIST.contains(event.eventType())) {
      return 0;
    }
    List<WebhookSubscription> subscriptions = tenantExecution.inTransaction(event.tenantId(),
        () -> webhooks.matching(event.tenantId(), event.eventType()));
    for (WebhookSubscription subscription : subscriptions) {
      Instant now = clock.instant();
      String payload = envelope(event);
      tenantExecution.inTransaction(event.tenantId(),
          () -> webhooks.createDelivery(subscription, event.tenantId(), event, payload,
              now, now));
    }
    return retryDue(event.tenantId(), 200);
  }

  public boolean deliver(Delivery delivery) {
    int attempt = delivery.attemptCount() + 1;
    Instant now = clock.instant();
    try {
      var uri = security.validate(delivery.subscription().url());
      String timestamp = Long.toString(delivery.signatureTimestamp().getEpochSecond());
      String signature = sign(secrets.resolve(delivery.subscription().secretReference()),
          timestamp + "." + delivery.payload());
      Map<String, String> headers = Map.of(
          "Content-Type", "application/json",
          "X-Cab-Event-ID", delivery.eventId().toString(),
          "X-Cab-Delivery-ID", delivery.id().toString(),
          "X-Cab-Signature-Timestamp", timestamp,
          "X-Cab-Signature", "v1=" + signature);
      WebhookTransport.Response response = transport.post(uri, delivery.payload(), headers,
          requestTimeout);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        tenantExecution.inTransaction(delivery.tenantId(),
            () -> webhooks.complete(
                delivery.tenantId(), delivery.id(), delivery.leaseToken(), attempt,
                response.statusCode(), now));
        return true;
      }
      retry(delivery, attempt, response.statusCode(), "HTTP_STATUS", now);
      return false;
    } catch (RuntimeException exception) {
      retry(delivery, attempt, null, "DELIVERY_FAILED", now);
      return false;
    }
  }

  public int retryDue(UUID tenantId, int limit) {
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("Webhook retry limit must be between 1 and 200");
    }
    int delivered = 0;
    List<Delivery> due = tenantExecution.inTransaction(
        tenantId, () -> {
          Instant now = clock.instant();
          return webhooks.claimDue(tenantId, limit, now, now.plus(leaseDuration), UUID.randomUUID());
        });
    for (Delivery delivery : due) {
      if (deliver(delivery)) {
        delivered++;
      }
    }
    return delivered;
  }

  private void retry(Delivery delivery, int attempt, Integer responseStatus, String error, Instant now) {
    boolean failed = attempt >= maxAttempts;
    long delaySeconds = Math.min(3600, 1L << Math.min(attempt, 12));
    tenantExecution.inTransaction(delivery.tenantId(),
        () -> webhooks.retry(delivery.tenantId(), delivery.id(), delivery.leaseToken(), attempt,
            responseStatus, error, now.plusSeconds(delaySeconds), failed, now));
  }

  private String envelope(OutboxEvent event) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", event.id());
    envelope.put("eventType", event.eventType());
    envelope.put("eventVersion", event.eventVersion());
    envelope.put("occurredAt", event.occurredAt());
    envelope.put("data", event.payload());
    try {
      return json.writeValueAsString(envelope);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Webhook payload cannot be serialized", exception);
    }
  }

  private String sign(String secret, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    }
  }
}

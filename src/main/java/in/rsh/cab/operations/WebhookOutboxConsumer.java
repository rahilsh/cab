package in.rsh.cab.operations;

import in.rsh.cab.webhook.WebhookDeliveryWorker;
import in.rsh.cab.webhook.WebhookService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class WebhookOutboxConsumer implements OutboxConsumer {

  private final WebhookDeliveryWorker worker;

  public WebhookOutboxConsumer(WebhookDeliveryWorker worker) {
    this.worker = worker;
  }

  @Override
  public boolean process(OutboxEvent event) {
    if (!WebhookService.EVENT_ALLOWLIST.contains(event.eventType())) {
      return false;
    }
    worker.process(event);
    return true;
  }
}

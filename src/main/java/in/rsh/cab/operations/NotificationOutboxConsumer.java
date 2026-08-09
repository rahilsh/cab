package in.rsh.cab.operations;

import in.rsh.cab.notification.NotificationDeliveryWorker;
import in.rsh.cab.notification.NotificationRecipientResolver;
import in.rsh.cab.tenancy.TenantExecution;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class NotificationOutboxConsumer implements OutboxConsumer {

  private final NotificationRecipientResolver recipients;
  private final NotificationDeliveryWorker worker;
  private final TenantExecution tenantExecution;

  public NotificationOutboxConsumer(
      NotificationRecipientResolver recipients, NotificationDeliveryWorker worker,
      TenantExecution tenantExecution) {
    this.recipients = recipients;
    this.worker = worker;
    this.tenantExecution = tenantExecution;
  }

  @Override
  public boolean process(OutboxEvent event) {
    List<UUID> routed = tenantExecution.inTransaction(
        event.tenantId(), () -> recipients.resolve(event));
    String template = event.eventType().replace('.', '_');
    String body = event.eventType().startsWith("safety.")
        ? "A safety incident has an update" : event.eventType();
    for (UUID recipient : routed) {
      worker.process(event, recipient, "LOCAL", template, event.eventVersion(), body);
    }
    return !routed.isEmpty();
  }
}

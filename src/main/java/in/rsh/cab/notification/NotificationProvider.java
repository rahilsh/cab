package in.rsh.cab.notification;

import java.util.UUID;

public interface NotificationProvider {

  String channel();

  String send(Message message);

  // Providers must deduplicate retries by the stable deliveryId.

  record Message(
      UUID deliveryId,
      UUID tenantId,
      UUID recipientAccountId,
      String eventType,
      String templateKey,
      int templateVersion,
      String body) {}
}

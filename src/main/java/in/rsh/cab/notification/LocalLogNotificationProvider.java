package in.rsh.cab.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalLogNotificationProvider implements NotificationProvider {

  private static final Logger log = LoggerFactory.getLogger(LocalLogNotificationProvider.class);

  @Override
  public String channel() {
    return "LOCAL";
  }

  @Override
  public String send(Message message) {
    log.info("Local notification delivery={} tenant={} recipient={} event={}", message.deliveryId(),
        message.tenantId(), message.recipientAccountId(), message.eventType());
    return "local:" + message.deliveryId();
  }
}

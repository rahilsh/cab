package in.rsh.cab.webhook;

import java.time.Duration;
import java.util.Map;

public interface WebhookTransport {

  Response post(
      ValidatedWebhookTarget target,
      String body,
      Map<String, String> headers,
      Duration timeout);

  record Response(int statusCode) {}
}

package in.rsh.cab.webhook;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface WebhookTransport {

  Response post(URI uri, String body, Map<String, String> headers, Duration timeout);

  record Response(int statusCode) {}
}

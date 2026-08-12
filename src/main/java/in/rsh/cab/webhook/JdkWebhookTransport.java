package in.rsh.cab.webhook;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JdkWebhookTransport implements WebhookTransport {

  private final HttpClient client;

  public JdkWebhookTransport(
      @Value("${webhooks.connect-timeout:PT5S}") Duration connectTimeout) {
    this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(connectTimeout).build();
  }

  @Override
  public Response post(URI uri, String body, Map<String, String> headers, Duration timeout) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout)
        .POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(request::header);
    try {
      return new Response(client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode());
    } catch (IOException exception) {
      throw new IllegalStateException("Webhook transport failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Webhook transport interrupted", exception);
    }
  }
}

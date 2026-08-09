package in.rsh.cab.webhook;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApacheWebhookTransport implements WebhookTransport {

  private final Duration connectTimeout;

  public ApacheWebhookTransport(
      @Value("${webhooks.connect-timeout:PT5S}") Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  @Override
  public Response post(
      ValidatedWebhookTarget target,
      String body,
      Map<String, String> headers,
      Duration timeout) {
    PoolingHttpClientConnectionManager connections =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(pinnedResolver(target))
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.of(connectTimeout))
                    .setSocketTimeout(Timeout.of(timeout))
                    .build())
            .build();
    RequestConfig requestConfig =
        RequestConfig.custom().setResponseTimeout(Timeout.of(timeout)).build();
    try (CloseableHttpClient client =
        HttpClients.custom()
            .setConnectionManager(connections)
            .setDefaultRequestConfig(requestConfig)
            .disableRedirectHandling()
            .disableContentCompression()
            .build()) {
      HttpPost request = new HttpPost(target.uri());
      request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
      headers.forEach(request::setHeader);
      try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
        int statusCode = response.getCode();
        return new Response(statusCode);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Webhook transport failed", exception);
    }
  }

  static DnsResolver pinnedResolver(ValidatedWebhookTarget target) {
    String targetHost = target.uri().getHost();
    InetAddress[] addresses = target.addresses().toArray(InetAddress[]::new);
    return new DnsResolver() {
      @Override
      public InetAddress[] resolve(String host) throws UnknownHostException {
        requireTargetHost(targetHost, host);
        return addresses.clone();
      }

      @Override
      public String resolveCanonicalHostname(String host) throws UnknownHostException {
        requireTargetHost(targetHost, host);
        return targetHost;
      }
    };
  }

  private static void requireTargetHost(String targetHost, String host) throws UnknownHostException {
    if (!targetHost.equalsIgnoreCase(host)) {
      throw new UnknownHostException("Unvalidated webhook host: " + host);
    }
  }
}

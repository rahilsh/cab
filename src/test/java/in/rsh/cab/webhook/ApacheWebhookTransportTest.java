package in.rsh.cab.webhook;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.Test;

class ApacheWebhookTransportTest {

  @Test
  void resolverPinsOriginalHostToPreviouslyValidatedAddresses() throws Exception {
    InetAddress selected =
        InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34});
    ValidatedWebhookTarget target =
        new ValidatedWebhookTarget(URI.create("https://example.com/hook"), List.of(selected));

    DnsResolver resolver = ApacheWebhookTransport.pinnedResolver(target);

    assertArrayEquals(new InetAddress[] {selected}, resolver.resolve("EXAMPLE.COM"));
    org.junit.jupiter.api.Assertions.assertEquals(
        "example.com", resolver.resolveCanonicalHostname("example.com"));
    assertThrows(UnknownHostException.class, () -> resolver.resolve("rebound.example"));
  }
}

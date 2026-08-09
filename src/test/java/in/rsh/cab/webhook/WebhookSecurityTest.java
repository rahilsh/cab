package in.rsh.cab.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import in.rsh.cab.exception.InvalidRequestException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookSecurityTest {

  @Test
  void acceptsOnlyHttpsHostsResolvingEntirelyToPublicAddresses() throws Exception {
    WebhookSecurity security = new WebhookSecurity(host -> List.of(
        InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34})));
    ValidatedWebhookTarget target = security.validate("https://example.com/hooks?id=1");
    assertEquals("example.com", target.uri().getHost());
    assertEquals("93.184.216.34", target.addresses().getFirst().getHostAddress());
    assertThrows(InvalidRequestException.class, () -> security.validate("http://example.com/hook"));
    assertThrows(InvalidRequestException.class, () -> security.validate("https://user@example.com/hook"));
    assertThrows(InvalidRequestException.class, () -> security.validate("https://example.com/hook#fragment"));
  }

  @Test
  void blocksLoopbackPrivateLinkLocalMetadataAndUnresolvedHosts() throws Exception {
    assertBlocked("127.0.0.1");
    assertBlocked("10.0.0.1");
    assertBlocked("169.254.169.254");
    assertBlocked("100.64.0.1");
    assertBlocked("198.18.0.1");
    assertBlocked("224.0.0.1");
    assertBlocked("fc00::1");
    WebhookSecurity empty = new WebhookSecurity(host -> List.of());
    assertThrows(InvalidRequestException.class, () -> empty.validate("https://example.com"));
    WebhookSecurity unresolved = new WebhookSecurity(host -> {
      throw new UnknownHostException(host);
    });
    assertThrows(InvalidRequestException.class, () -> unresolved.validate("https://missing.example"));
  }

  private void assertBlocked(String value) throws Exception {
    WebhookSecurity security = new WebhookSecurity(host -> List.of(InetAddress.getByName(value)));
    assertThrows(InvalidRequestException.class, () -> security.validate("https://example.com/hook"));
  }
}

package in.rsh.cab.webhook;

import in.rsh.cab.exception.InvalidRequestException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

public class WebhookSecurity {

  private final HostResolver resolver;

  public WebhookSecurity(HostResolver resolver) {
    this.resolver = resolver;
  }

  public URI validate(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException("Webhook URL is invalid");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
        || uri.getUserInfo() != null || uri.getFragment() != null) {
      throw new InvalidRequestException("Webhook URL must be an absolute HTTPS URL without credentials or fragment");
    }
    List<InetAddress> addresses;
    try {
      addresses = resolver.resolve(uri.getHost());
    } catch (UnknownHostException exception) {
      throw new InvalidRequestException("Webhook host cannot be resolved");
    }
    if (addresses.isEmpty() || addresses.stream().anyMatch(this::isBlocked)) {
      throw new InvalidRequestException("Webhook host resolves to a non-public address");
    }
    return uri;
  }

  private boolean isBlocked(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (address instanceof Inet4Address) {
      int first = Byte.toUnsignedInt(bytes[0]);
      int second = Byte.toUnsignedInt(bytes[1]);
      return first == 0 || first == 127 || first >= 224
          || (first == 100 && second >= 64 && second <= 127)
          || (first == 192 && second == 0)
          || (first == 198 && (second == 18 || second == 19));
    }
    return (bytes[0] & 0xfe) == 0xfc;
  }

  @FunctionalInterface
  public interface HostResolver {
    List<InetAddress> resolve(String host) throws UnknownHostException;
  }
}

package in.rsh.cab.webhook;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

public record ValidatedWebhookTarget(URI uri, List<InetAddress> addresses) {

  public ValidatedWebhookTarget {
    addresses = List.copyOf(addresses);
    if (addresses.isEmpty()) {
      throw new IllegalArgumentException("Validated webhook target requires an address");
    }
  }
}

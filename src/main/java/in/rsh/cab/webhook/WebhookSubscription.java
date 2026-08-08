package in.rsh.cab.webhook;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WebhookSubscription(
    UUID id,
    String url,
    String secretReference,
    Set<String> eventFilters,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  public WebhookSubscription {
    eventFilters = Set.copyOf(eventFilters);
  }
}

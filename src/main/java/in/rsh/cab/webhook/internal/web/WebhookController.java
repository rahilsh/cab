package in.rsh.cab.webhook.internal.web;

import in.rsh.cab.webhook.WebhookService;
import in.rsh.cab.webhook.WebhookSubscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/webhook-subscriptions")
public class WebhookController {

  private final WebhookService webhooks;

  public WebhookController(WebhookService webhooks) {
    this.webhooks = webhooks;
  }

  @PostMapping
  public ResponseEntity<WebhookSubscription> create(@Valid @RequestBody SubscriptionRequest request) {
    WebhookSubscription subscription = webhooks.create(request.url(), request.secretReference(),
        request.eventFilters(), request.enabled());
    return ResponseEntity.created(URI.create("/api/v1/admin/webhook-subscriptions/"
        + subscription.id())).body(subscription);
  }

  @GetMapping
  public List<WebhookSubscription> list() {
    return webhooks.list();
  }

  @PutMapping("/{id}")
  public WebhookSubscription update(@PathVariable UUID id,
      @Valid @RequestBody SubscriptionRequest request) {
    return webhooks.update(id, request.version(), request.url(), request.secretReference(),
        request.eventFilters(), request.enabled());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    webhooks.delete(id);
    return ResponseEntity.noContent().build();
  }

  public record SubscriptionRequest(
      @NotBlank @Size(max = 2048) String url,
      @NotBlank @Pattern(regexp = "^env:[A-Za-z_][A-Za-z0-9_]*$") String secretReference,
      @NotEmpty Set<@NotBlank @Size(max = 160) String> eventFilters,
      boolean enabled,
      @PositiveOrZero long version) {}
}

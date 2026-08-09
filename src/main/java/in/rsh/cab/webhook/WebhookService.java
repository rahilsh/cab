package in.rsh.cab.webhook;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import in.rsh.cab.webhook.internal.persistence.WebhookRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookService {

  public static final Set<String> EVENT_ALLOWLIST = Set.of(
      "ride.created", "ride.status_changed", "ride.completed", "ride.cancelled",
      "ride.driver_assigned", "ride.no_driver",
      "rating.created", "support.case_created", "support.case_state_changed");
  private final WebhookRepository subscriptions;
  private final WebhookSecurity security;
  private final AuditService audit;
  private final ObjectMapper json;
  private final Clock clock;

  public WebhookService(
      WebhookRepository subscriptions, WebhookSecurity security, AuditService audit,
      ObjectMapper json, Clock clock) {
    this.subscriptions = subscriptions;
    this.security = security;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public WebhookSubscription create(
      String url, String secretReference, Set<String> eventFilters, boolean enabled) {
    TenantContext context = requireAdmin();
    validate(url, secretReference, eventFilters);
    Instant now = clock.instant();
    WebhookSubscription subscription = new WebhookSubscription(UUID.randomUUID(), url,
        secretReference, eventFilters, enabled, now, now, 0);
    subscriptions.insert(context.tenantId(), subscription);
    audit(context, subscription.id(), "webhook_subscription.create", enabled, eventFilters);
    return subscription;
  }

  @Transactional(readOnly = true)
  public List<WebhookSubscription> list() {
    TenantContext context = requireAdmin();
    return subscriptions.findAll(context.tenantId());
  }

  @Transactional
  public WebhookSubscription update(UUID id, long version, String url, String secretReference,
      Set<String> eventFilters, boolean enabled) {
    TenantContext context = requireAdmin();
    validate(url, secretReference, eventFilters);
    WebhookSubscription current = subscriptions.find(context.tenantId(), id)
        .orElseThrow(() -> new NotFoundException("Webhook subscription not found"));
    if (current.version() != version) {
      throw new ConflictException("Webhook subscription changed concurrently");
    }
    WebhookSubscription updated = new WebhookSubscription(id, url, secretReference, eventFilters,
        enabled, current.createdAt(), clock.instant(), version + 1);
    if (!subscriptions.update(context.tenantId(), updated, version)) {
      throw new ConflictException("Webhook subscription changed concurrently");
    }
    audit(context, id, "webhook_subscription.update", enabled, eventFilters);
    return updated;
  }

  @Transactional
  public void delete(UUID id) {
    TenantContext context = requireAdmin();
    subscriptions.find(context.tenantId(), id)
        .orElseThrow(() -> new NotFoundException("Webhook subscription not found"));
    subscriptions.delete(context.tenantId(), id, clock.instant());
    audit(context, id, "webhook_subscription.delete", false, Set.of());
  }

  private void validate(String url, String secretReference, Set<String> eventFilters) {
    security.validate(url);
    if (secretReference == null || !secretReference.matches("^env:CAB_WEBHOOK_[A-Z0-9_]+$")) {
      throw new InvalidRequestException(
          "Webhook secret must use an env:CAB_WEBHOOK_ reference");
    }
    if (eventFilters == null || eventFilters.isEmpty()
        || !EVENT_ALLOWLIST.containsAll(eventFilters)) {
      throw new InvalidRequestException("Webhook event filters contain an unsupported event");
    }
  }

  private TenantContext requireAdmin() {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.TENANT_ADMIN)) {
      throw new TenantAccessDeniedException("TENANT_ADMIN role is required");
    }
    return context;
  }

  private void audit(TenantContext context, UUID id, String action, boolean enabled,
      Set<String> filters) {
    audit.record(context.tenantId(), context.accountId(), action, "webhook_subscription", id,
        "SUCCESS", json.valueToTree(new WebhookAudit(enabled, filters)));
  }

  private record WebhookAudit(boolean enabled, Set<String> eventFilters) {}
}

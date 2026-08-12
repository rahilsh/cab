package in.rsh.cab.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import in.rsh.cab.webhook.internal.persistence.WebhookRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WebhookServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private static final Set<String> FILTERS = Set.of("ride.completed");
  private final WebhookRepository repository = mock(WebhookRepository.class);
  private final WebhookSecurity security = mock(WebhookSecurity.class);
  private final AuditService audit = mock(AuditService.class);
  private final WebhookService service = new WebhookService(
      repository, security, audit, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    TenantContext.set(new TenantContext(
        TENANT, ACCOUNT, UUID.randomUUID(), Set.of(TenantRole.TENANT_ADMIN)));
    when(security.validate(any())).thenReturn(URI.create("https://example.com/hook"));
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void createsAndListsTenantSubscriptionsWithAudit() {
    WebhookSubscription created =
        service.create("https://example.com/hook", "env:CAB_WEBHOOK_SECRET", FILTERS, true);

    assertEquals("https://example.com/hook", created.url());
    assertEquals(NOW, created.createdAt());
    verify(repository).insert(TENANT, created);
    verify(audit).record(
        eq(TENANT), eq(ACCOUNT), eq("webhook_subscription.create"),
        eq("webhook_subscription"), eq(created.id()), eq("SUCCESS"), any());

    when(repository.findAll(TENANT)).thenReturn(List.of(created));
    assertEquals(List.of(created), service.list());
    assertTrue(WebhookService.EVENT_ALLOWLIST.contains("ride.driver_assigned"));
    assertTrue(WebhookService.EVENT_ALLOWLIST.contains("ride.no_driver"));
  }

  @Test
  void updatesAndDeletesExistingSubscriptionOptimistically() {
    UUID id = UUID.randomUUID();
    WebhookSubscription current = subscription(id, 2);
    when(repository.find(TENANT, id)).thenReturn(Optional.of(current));
    when(repository.update(eq(TENANT), any(), eq(2L))).thenReturn(true);

    WebhookSubscription updated = service.update(
        id, 2, "https://example.com/updated", "env:CAB_WEBHOOK_UPDATED_SECRET", FILTERS, false);

    assertEquals(3, updated.version());
    assertEquals(current.createdAt(), updated.createdAt());
    assertEquals(NOW, updated.updatedAt());
    service.delete(id);
    verify(repository).delete(TENANT, id, NOW);
  }

  @Test
  void rejectsInvalidInputMissingResourcesAndConcurrentUpdates() {
    assertThrows(
        InvalidRequestException.class,
        () -> service.create("https://example.com", "secret", FILTERS, true));
    assertThrows(
        InvalidRequestException.class,
        () -> service.create("https://example.com", "env:CAB_WEBHOOK_SECRET", Set.of("payment.captured"), true));

    UUID missing = UUID.randomUUID();
    when(repository.find(TENANT, missing)).thenReturn(Optional.empty());
    assertThrows(
        NotFoundException.class,
        () -> service.update(
            missing, 0, "https://example.com", "env:CAB_WEBHOOK_SECRET", FILTERS, true));
    assertThrows(NotFoundException.class, () -> service.delete(missing));

    UUID stale = UUID.randomUUID();
    when(repository.find(TENANT, stale)).thenReturn(Optional.of(subscription(stale, 2)));
    assertThrows(
        ConflictException.class,
        () -> service.update(
            stale, 1, "https://example.com", "env:CAB_WEBHOOK_SECRET", FILTERS, true));

    UUID raced = UUID.randomUUID();
    when(repository.find(TENANT, raced)).thenReturn(Optional.of(subscription(raced, 2)));
    when(repository.update(eq(TENANT), any(), eq(2L))).thenReturn(false);
    assertThrows(
        ConflictException.class,
        () -> service.update(
            raced, 2, "https://example.com", "env:CAB_WEBHOOK_SECRET", FILTERS, true));
  }

  @Test
  void requiresTenantAdministratorRole() {
    TenantContext.set(
        new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(TenantRole.RIDER)));
    TenantAccessDeniedException exception =
        assertThrows(TenantAccessDeniedException.class, service::list);
    assertTrue(exception.getMessage().contains("TENANT_ADMIN"));
  }

  private WebhookSubscription subscription(UUID id, long version) {
    return new WebhookSubscription(
        id,
        "https://example.com/hook",
        "env:CAB_WEBHOOK_SECRET",
        FILTERS,
        true,
        NOW.minusSeconds(60),
        NOW.minusSeconds(30),
        version);
  }
}

package in.rsh.cab.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.internal.persistence.AuditRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AuditServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
  private final AuditRepository repository = mock(AuditRepository.class);
  private final AuditService service =
      new AuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void recordsRedactedSummaryAndListsForAdmin() {
    var summary = new ObjectMapper().createObjectNode().put("totalMinor", 900);
    UUID target = UUID.randomUUID();
    UUID id = service.record(TENANT, ACTOR, "quote.create", "quote", target, "SUCCESS", summary);
    verify(repository).insert(
        id, TENANT, ACTOR, "quote.create", "quote", target, "SUCCESS", null, summary, NOW);

    AuditEvent event = new AuditEvent(
        id, ACTOR, "quote.create", "quote", target, "SUCCESS", null, summary, NOW);
    when(repository.findLatest(TENANT, 50)).thenReturn(List.of(event));
    context(TenantRole.TENANT_ADMIN);
    assertEquals(List.of(event), service.list(50));
  }

  @Test
  void allowsSupportButRejectsOtherRolesAndInvalidLimits() {
    context(TenantRole.SUPPORT);
    service.list(1);

    context(TenantRole.RIDER);
    assertThrows(TenantAccessDeniedException.class, () -> service.list(1));
    context(TenantRole.TENANT_ADMIN);
    assertThrows(IllegalArgumentException.class, () -> service.list(0));
    assertThrows(IllegalArgumentException.class, () -> service.list(201));
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACTOR, UUID.randomUUID(), Set.of(role)));
  }
}

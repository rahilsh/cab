package in.rsh.cab.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.support.internal.persistence.SupportRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SupportServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final UUID RIDE = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final SupportRepository repository = mock(SupportRepository.class);
  private final SupportService service = new SupportService(repository, mock(OutboxService.class),
      mock(AuditService.class), new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void riderCreatesAndListsOwnCaseWhileStaffListsAndTriagesAll() {
    context(TenantRole.RIDER);
    when(repository.isRideParticipant(TENANT, RIDE, ACCOUNT)).thenReturn(true);
    SupportCase supportCase = service.create(RIDE, "Receipt", "Question");
    verify(repository).insert(TENANT, supportCase, supportCase.messages().getFirst());
    when(repository.findOwn(TENANT, ACCOUNT)).thenReturn(List.of(supportCase));
    assertEquals(1, service.list().size());

    context(TenantRole.SUPPORT);
    when(repository.findAll(TENANT)).thenReturn(List.of(supportCase));
    assertEquals(1, service.list().size());
    when(repository.find(TENANT, supportCase.id())).thenReturn(Optional.of(supportCase), Optional.of(
        new SupportCase(supportCase.id(), ACCOUNT, RIDE, "Receipt", "IN_PROGRESS", "NORMAL",
            NOW, NOW, 1, List.of())));
    when(repository.updateState(TENANT, supportCase.id(), "OPEN", "IN_PROGRESS", 0, NOW))
        .thenReturn(true);
    assertEquals("IN_PROGRESS",
        service.changeState(supportCase.id(), "OPEN", "IN_PROGRESS", 0, "triaged").state());
  }

  @Test
  void rejectsForeignRideInternalMessageAndInvalidState() {
    context(TenantRole.RIDER);
    assertThrows(TenantAccessDeniedException.class,
        () -> service.create(RIDE, "Receipt", "Question"));
    SupportCase supportCase = new SupportCase(UUID.randomUUID(), ACCOUNT, null, "Receipt", "OPEN",
        "NORMAL", NOW, NOW, 0, List.of());
    when(repository.find(TENANT, supportCase.id())).thenReturn(Optional.of(supportCase));
    assertThrows(TenantAccessDeniedException.class,
        () -> service.addMessage(supportCase.id(), 0, "private", true));
    context(TenantRole.TENANT_ADMIN);
    assertThrows(InvalidRequestException.class,
        () -> service.changeState(supportCase.id(), "OPEN", "INVALID", 0, null));
    assertThrows(ConflictException.class,
        () -> service.changeState(supportCase.id(), "OPEN", "CLOSED", 1, null));
  }

  @Test
  void messageRequiresCurrentVersionAndOpenCase() {
    context(TenantRole.RIDER);
    SupportCase open = new SupportCase(UUID.randomUUID(), ACCOUNT, null, "Receipt", "OPEN",
        "NORMAL", NOW, NOW, 2, List.of());
    when(repository.find(TENANT, open.id())).thenReturn(Optional.of(open), Optional.of(
        new SupportCase(open.id(), ACCOUNT, null, "Receipt", "OPEN", "NORMAL", NOW, NOW, 3,
            List.of())));
    when(repository.appendMessage(org.mockito.ArgumentMatchers.eq(TENANT),
        org.mockito.ArgumentMatchers.eq(open.id()), org.mockito.ArgumentMatchers.eq(2L),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(NOW))).thenReturn(true);
    assertEquals(3, service.addMessage(open.id(), 2, "reply", false).version());

    SupportCase closed = new SupportCase(UUID.randomUUID(), ACCOUNT, null, "Receipt", "CLOSED",
        "NORMAL", NOW, NOW, 4, List.of());
    when(repository.find(TENANT, closed.id())).thenReturn(Optional.of(closed));
    assertThrows(ConflictException.class,
        () -> service.addMessage(closed.id(), 4, "late reply", false));
    assertThrows(ConflictException.class,
        () -> service.addMessage(open.id(), 1, "stale reply", false));
  }

  @Test
  void closedCaseCannotReopenAndAssignmentRequiresPersistedStaffRole() {
    context(TenantRole.SUPPORT);
    UUID assignee = UUID.randomUUID();
    SupportCase closed = new SupportCase(UUID.randomUUID(), ACCOUNT, null, "Receipt", "CLOSED",
        "NORMAL", NOW, NOW, 3, List.of());
    when(repository.find(TENANT, closed.id())).thenReturn(Optional.of(closed));

    assertThrows(ConflictException.class,
        () -> service.changeState(closed.id(), "CLOSED", "IN_PROGRESS", 3, null));
    assertThrows(InvalidRequestException.class, () -> service.assign(closed.id(), assignee, 3));
    assertThrows(ConflictException.class, () -> service.assign(closed.id(), assignee, 2));

    when(repository.hasStaffRole(TENANT, assignee)).thenReturn(true);
    when(repository.assign(TENANT, closed.id(), assignee, ACCOUNT, 3, NOW)).thenReturn(true);
    service.assign(closed.id(), assignee, 3);
    verify(repository).assign(TENANT, closed.id(), assignee, ACCOUNT, 3, NOW);
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

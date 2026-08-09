package in.rsh.cab.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.safety.internal.persistence.SafetyRepository;
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

class SafetyServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final UUID RIDE = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final SafetyRepository repository = mock(SafetyRepository.class);
  private final SafetyService service = new SafetyService(repository, mock(OutboxService.class),
      mock(AuditService.class), new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void participantReportsAndSafetyStaffTriages() {
    context(TenantRole.RIDER);
    when(repository.isRideParticipant(TENANT, RIDE, ACCOUNT)).thenReturn(true);
    SafetyIncident incident = service.report(RIDE, "UNSAFE_DRIVING", "hard braking");
    assertEquals("REPORTED", incident.state());
    verify(repository).insert(TENANT, incident);

    context(TenantRole.SAFETY);
    when(repository.find(TENANT, incident.id())).thenReturn(Optional.of(incident), Optional.of(
        new SafetyIncident(incident.id(), RIDE, ACCOUNT, incident.category(), incident.description(),
            "TRIAGED", "HIGH", NOW, NOW, 1, List.of())));
    when(repository.update(TENANT, incident.id(), "REPORTED", "TRIAGED", "HIGH", 0, NOW))
        .thenReturn(true);
    assertEquals("TRIAGED", service.action(incident.id(), "TRIAGE", "REPORTED", "TRIAGED",
        "HIGH", 0, null).state());
    when(repository.findAll(TENANT)).thenReturn(List.of(incident));
    assertEquals(1, service.listRestricted().size());
  }

  @Test
  void rejectsNonParticipantRestrictedReadAndUrlEvidence() {
    context(TenantRole.RIDER);
    assertThrows(TenantAccessDeniedException.class,
        () -> service.report(RIDE, "UNSAFE_DRIVING", "hard braking"));
    assertThrows(TenantAccessDeniedException.class, service::listRestricted);
    SafetyIncident incident = new SafetyIncident(UUID.randomUUID(), RIDE, ACCOUNT, "OTHER", "x",
        "REPORTED", "UNASSESSED", NOW, NOW, 0, List.of());
    when(repository.find(TENANT, incident.id())).thenReturn(Optional.of(incident));
    assertThrows(InvalidRequestException.class, () -> service.addEvidence(incident.id(),
        "https://objects.example/evidence", "image/jpeg", 10L, null));
    SafetyIncident.Evidence evidence = service.addEvidence(incident.id(),
        "tenant/incidents/photo.jpg", "image/jpeg", 10L, null);
    verify(repository).insertEvidence(TENANT, incident.id(), evidence);
  }

  @Test
  void closedIncidentCannotReopenAndParticipantCanGetHydratedIncident() {
    SafetyIncident.Evidence evidence = new SafetyIncident.Evidence(UUID.randomUUID(), ACCOUNT,
        "incident/photo.jpg", "image/jpeg", 10L, null, NOW);
    SafetyIncident closed = new SafetyIncident(UUID.randomUUID(), RIDE, ACCOUNT, "OTHER", "x",
        "CLOSED", "HIGH", NOW, NOW, 4, List.of(evidence));
    when(repository.find(TENANT, closed.id())).thenReturn(Optional.of(closed));

    context(TenantRole.SAFETY);
    assertThrows(ConflictException.class, () -> service.action(closed.id(), "REOPEN", "CLOSED",
        "INVESTIGATING", "HIGH", 4, null));

    context(TenantRole.RIDER);
    when(repository.isRideParticipant(TENANT, RIDE, ACCOUNT)).thenReturn(true);
    assertEquals(List.of(evidence), service.get(closed.id()).evidence());
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

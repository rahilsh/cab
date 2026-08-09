package in.rsh.cab.support;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.support.internal.persistence.SupportRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SupportService {

  private static final Map<String, Set<String>> TRANSITIONS = Map.of(
      "OPEN", Set.of("IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED"),
      "IN_PROGRESS", Set.of("WAITING", "RESOLVED", "CLOSED"),
      "WAITING", Set.of("IN_PROGRESS", "RESOLVED", "CLOSED"),
      "RESOLVED", Set.of("IN_PROGRESS", "CLOSED"),
      "CLOSED", Set.of());
  private final SupportRepository cases;
  private final OutboxService outbox;
  private final AuditService audit;
  private final ObjectMapper json;
  private final Clock clock;

  public SupportService(
      SupportRepository cases, OutboxService outbox, AuditService audit,
      ObjectMapper json, Clock clock) {
    this.cases = cases;
    this.outbox = outbox;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public SupportCase create(UUID rideId, String subject, String message) {
    TenantContext context = requireAny(TenantRole.RIDER, TenantRole.DRIVER);
    if (rideId != null && !cases.isRideParticipant(context.tenantId(), rideId, context.accountId())) {
      throw new TenantAccessDeniedException("Support case ride must belong to the caller");
    }
    Instant now = clock.instant();
    SupportCase.Message first = new SupportCase.Message(
        UUID.randomUUID(), context.accountId(), message, false, now);
    SupportCase supportCase = new SupportCase(UUID.randomUUID(), context.accountId(), rideId,
        subject, "OPEN", "NORMAL", now, now, 0, List.of(first));
    cases.insert(context.tenantId(), supportCase, first);
    outbox.append(context.tenantId(), "support_case", supportCase.id(), 0,
        "support.case_created", 1,
        json.valueToTree(new CaseEvent(supportCase.id(), rideId, "OPEN")), null);
    audit.record(context.tenantId(), context.accountId(), "support_case.create", "support_case",
        supportCase.id(), "SUCCESS", json.valueToTree(new CaseAudit(rideId, "OPEN")));
    return supportCase;
  }

  @Transactional(readOnly = true)
  public List<SupportCase> list() {
    TenantContext context = TenantContext.require();
    if (isStaff(context)) {
      return cases.findAll(context.tenantId());
    }
    requireAny(TenantRole.RIDER, TenantRole.DRIVER);
    return cases.findOwn(context.tenantId(), context.accountId()).stream()
        .map(this::withoutInternalMessages).toList();
  }

  @Transactional(readOnly = true)
  public SupportCase get(UUID caseId) {
    TenantContext context = TenantContext.require();
    SupportCase supportCase = cases.find(context.tenantId(), caseId)
        .orElseThrow(() -> new NotFoundException("Support case not found"));
    if (isStaff(context)) {
      return supportCase;
    }
    requireAny(TenantRole.RIDER, TenantRole.DRIVER);
    if (!supportCase.openedByAccountId().equals(context.accountId())) {
      throw new NotFoundException("Support case not found");
    }
    return withoutInternalMessages(supportCase);
  }

  @Transactional
  public SupportCase addMessage(UUID caseId, String body, boolean internal) {
    TenantContext context = TenantContext.require();
    SupportCase supportCase = cases.find(context.tenantId(), caseId)
        .orElseThrow(() -> new NotFoundException("Support case not found"));
    if (internal && !isStaff(context)) {
      throw new TenantAccessDeniedException("Only support staff can add internal messages");
    }
    if (!isStaff(context) && !supportCase.openedByAccountId().equals(context.accountId())) {
      throw new NotFoundException("Support case not found");
    }
    cases.insertMessage(context.tenantId(), caseId,
        new SupportCase.Message(UUID.randomUUID(), context.accountId(), body, internal, clock.instant()));
    SupportCase updated = cases.find(context.tenantId(), caseId).orElseThrow();
    return isStaff(context) ? updated : withoutInternalMessages(updated);
  }

  @Transactional
  public SupportCase changeState(
      UUID caseId, String expectedState, String next, long expectedVersion, String reason) {
    TenantContext context = requireStaff();
    if (!TRANSITIONS.containsKey(expectedState) || !TRANSITIONS.containsKey(next)) {
      throw new InvalidRequestException("Unsupported support case state");
    }
    SupportCase current = cases.find(context.tenantId(), caseId)
        .orElseThrow(() -> new NotFoundException("Support case not found"));
    if (current.version() != expectedVersion || !current.state().equals(expectedState)) {
      throw new ConflictException("Support case state or version is stale");
    }
    if (!TRANSITIONS.get(current.state()).contains(next)) {
      throw new ConflictException("Support case cannot transition from " + current.state() + " to " + next);
    }
    Instant now = clock.instant();
    if (!cases.updateState(
        context.tenantId(), caseId, expectedState, next, expectedVersion, now)) {
      throw new ConflictException("Support case changed concurrently");
    }
    cases.appendState(context.tenantId(), caseId, current.state(), next, context.accountId(), reason, now);
    outbox.append(context.tenantId(), "support_case", caseId, expectedVersion + 1,
        "support.case_state_changed", 1,
        json.valueToTree(new CaseEvent(caseId, current.rideId(), next)), null);
    audit.record(context.tenantId(), context.accountId(), "support_case.state", "support_case",
        caseId, "SUCCESS", json.valueToTree(new StateAudit(current.state(), next)));
    return cases.find(context.tenantId(), caseId).orElseThrow();
  }

  @Transactional
  public void assign(UUID caseId, UUID assigneeId, long expectedVersion) {
    TenantContext context = requireStaff();
    SupportCase supportCase = cases.find(context.tenantId(), caseId)
        .orElseThrow(() -> new NotFoundException("Support case not found"));
    if (supportCase.version() != expectedVersion) {
      throw new ConflictException("Support case changed concurrently");
    }
    if (!cases.hasStaffRole(context.tenantId(), assigneeId)) {
      throw new InvalidRequestException("Assignee must have SUPPORT or TENANT_ADMIN role");
    }
    if (!cases.assign(context.tenantId(), caseId, assigneeId, context.accountId(),
        expectedVersion, clock.instant())) {
      throw new ConflictException("Support case changed concurrently");
    }
    audit.record(context.tenantId(), context.accountId(), "support_case.assign", "support_case",
        caseId, "SUCCESS", json.valueToTree(new AssignmentAudit(assigneeId)));
  }

  private TenantContext requireStaff() {
    return requireAny(TenantRole.SUPPORT, TenantRole.TENANT_ADMIN);
  }

  private TenantContext requireAny(TenantRole... roles) {
    TenantContext context = TenantContext.require();
    if (Set.of(roles).stream().noneMatch(context.roles()::contains)) {
      throw new TenantAccessDeniedException("Required tenant role is missing");
    }
    return context;
  }

  private boolean isStaff(TenantContext context) {
    return context.roles().contains(TenantRole.SUPPORT)
        || context.roles().contains(TenantRole.TENANT_ADMIN);
  }

  private SupportCase withoutInternalMessages(SupportCase supportCase) {
    return new SupportCase(supportCase.id(), supportCase.openedByAccountId(), supportCase.rideId(),
        supportCase.subject(), supportCase.state(), supportCase.priority(), supportCase.createdAt(),
        supportCase.updatedAt(), supportCase.version(), supportCase.messages().stream()
            .filter(message -> !message.internal()).toList());
  }

  private record CaseAudit(UUID rideId, String state) {}

  private record CaseEvent(UUID caseId, UUID rideId, String state) {}

  private record StateAudit(String from, String to) {}

  private record AssignmentAudit(UUID assigneeAccountId) {}
}

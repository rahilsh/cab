package in.rsh.cab.safety;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.safety.internal.persistence.SafetyRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SafetyService {

  private static final Map<String, Set<String>> TRANSITIONS = Map.of(
      "REPORTED", Set.of("TRIAGED", "CLOSED"),
      "TRIAGED", Set.of("INVESTIGATING", "RESOLVED", "CLOSED"),
      "INVESTIGATING", Set.of("RESOLVED", "CLOSED"),
      "RESOLVED", Set.of("INVESTIGATING", "CLOSED"),
      "CLOSED", Set.of());
  private static final Set<String> SEVERITIES =
      Set.of("UNASSESSED", "LOW", "MEDIUM", "HIGH", "CRITICAL");
  private static final Pattern OBJECT_KEY =
      Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,511}$");
  private final SafetyRepository incidents;
  private final OutboxService outbox;
  private final AuditService audit;
  private final ObjectMapper json;
  private final Clock clock;

  public SafetyService(
      SafetyRepository incidents, OutboxService outbox, AuditService audit,
      ObjectMapper json, Clock clock) {
    this.incidents = incidents;
    this.outbox = outbox;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public SafetyIncident report(UUID rideId, String category, String description) {
    TenantContext context = requireAny(TenantRole.RIDER, TenantRole.DRIVER);
    if (!incidents.isRideParticipant(context.tenantId(), rideId, context.accountId())) {
      throw new TenantAccessDeniedException("Only ride participants can report an incident");
    }
    Instant now = clock.instant();
    SafetyIncident incident = new SafetyIncident(UUID.randomUUID(), rideId, context.accountId(),
        category, description, "REPORTED", "UNASSESSED", now, now, 0, List.of());
    incidents.insert(context.tenantId(), incident);
    incidents.appendAction(context.tenantId(), incident.id(), context.accountId(), "REPORTED",
        null, "REPORTED", null, now);
    // Safety events deliberately stay off the outbound webhook allowlist.
    outbox.append(context.tenantId(), "safety_incident", incident.id(), 0,
        "safety.incident_reported", 1,
        json.valueToTree(new SafetyEvent(
            incident.id(), rideId, context.accountId(), category)), null);
    audit.record(context.tenantId(), context.accountId(), "safety_incident.report",
        "safety_incident", incident.id(), "SUCCESS",
        json.valueToTree(new SafetyAudit(rideId, category, "REPORTED")));
    return incident;
  }

  @Transactional(readOnly = true)
  public List<SafetyIncident> listRestricted() {
    TenantContext context = requireRestricted();
    return incidents.findAll(context.tenantId());
  }

  @Transactional(readOnly = true)
  public SafetyIncident get(UUID incidentId) {
    TenantContext context = TenantContext.require();
    SafetyIncident incident = incidents.find(context.tenantId(), incidentId)
        .orElseThrow(() -> new NotFoundException("Safety incident not found"));
    if (!isRestricted(context)) {
      requireAny(TenantRole.RIDER, TenantRole.DRIVER);
    }
    if (!isRestricted(context) && !incidents.isRideParticipant(
        context.tenantId(), incident.rideId(), context.accountId())) {
      throw new NotFoundException("Safety incident not found");
    }
    return incident;
  }

  @Transactional
  public SafetyIncident.Evidence addEvidence(
      UUID incidentId, long expectedVersion, String objectKey, String mediaType, Long sizeBytes,
      String checksum) {
    TenantContext context = TenantContext.require();
    SafetyIncident incident = incidents.find(context.tenantId(), incidentId)
        .orElseThrow(() -> new NotFoundException("Safety incident not found"));
    boolean staff = isRestricted(context);
    if (!staff && !incident.reportedByAccountId().equals(context.accountId())) {
      throw new TenantAccessDeniedException("Safety incident access is restricted");
    }
    if (incident.version() != expectedVersion || "CLOSED".equals(incident.state())) {
      throw new ConflictException("Safety incident changed or is closed");
    }
    if (!OBJECT_KEY.matcher(objectKey).matches() || objectKey.contains("../")
        || objectKey.contains("/..") || objectKey.contains(":")) {
      throw new InvalidRequestException("Evidence must be an external object key, not a URL or path traversal");
    }
    SafetyIncident.Evidence evidence = new SafetyIncident.Evidence(UUID.randomUUID(),
        context.accountId(), objectKey, mediaType, sizeBytes, checksum, clock.instant());
    if (!incidents.appendEvidence(
        context.tenantId(), incidentId, expectedVersion, evidence, evidence.createdAt())) {
      throw new ConflictException("Safety incident changed or is closed");
    }
    outbox.append(context.tenantId(), "safety_incident", incidentId, expectedVersion + 1,
        "safety.evidence_added", 1,
        json.valueToTree(new SafetyUpdateEvent(incidentId, incident.rideId())), null);
    audit.record(context.tenantId(), context.accountId(), "safety_evidence.add", "safety_incident",
        incidentId, "SUCCESS", json.valueToTree(new EvidenceAudit(evidence.id(), mediaType)));
    return evidence;
  }

  @Transactional
  public SafetyIncident action(
      UUID incidentId, String action, String expectedState, String state, String severity,
      long expectedVersion, String note) {
    TenantContext context = requireRestricted();
    if (!TRANSITIONS.containsKey(expectedState) || !TRANSITIONS.containsKey(state)
        || !SEVERITIES.contains(severity)) {
      throw new InvalidRequestException("Unsupported safety state or severity");
    }
    SafetyIncident current = incidents.find(context.tenantId(), incidentId)
        .orElseThrow(() -> new NotFoundException("Safety incident not found"));
    if (current.version() != expectedVersion || !current.state().equals(expectedState)) {
      throw new ConflictException("Safety incident state or version is stale");
    }
    if (!TRANSITIONS.get(current.state()).contains(state)) {
      throw new ConflictException(
          "Safety incident cannot transition from " + current.state() + " to " + state);
    }
    Instant now = clock.instant();
    if (!incidents.update(context.tenantId(), incidentId, expectedState, state, severity,
        expectedVersion, now)) {
      throw new ConflictException("Safety incident changed concurrently");
    }
    incidents.appendAction(context.tenantId(), incidentId, context.accountId(), action,
        current.state(), state, note, now);
    outbox.append(context.tenantId(), "safety_incident", incidentId, expectedVersion + 1,
        "safety.incident_updated", 1,
        json.valueToTree(new SafetyUpdateEvent(incidentId, current.rideId())), null);
    audit.record(context.tenantId(), context.accountId(), "safety_incident.action",
        "safety_incident", incidentId, "SUCCESS",
        json.valueToTree(new ActionAudit(action, state, severity)));
    return incidents.find(context.tenantId(), incidentId).orElseThrow();
  }

  private TenantContext requireRestricted() {
    return requireAny(TenantRole.SAFETY, TenantRole.TENANT_ADMIN);
  }

  private TenantContext requireAny(TenantRole... roles) {
    TenantContext context = TenantContext.require();
    if (Set.of(roles).stream().noneMatch(context.roles()::contains)) {
      throw new TenantAccessDeniedException("Required tenant role is missing");
    }
    return context;
  }

  private boolean isRestricted(TenantContext context) {
    return context.roles().contains(TenantRole.SAFETY)
        || context.roles().contains(TenantRole.TENANT_ADMIN);
  }

  private record SafetyEvent(
      UUID incidentId, UUID rideId, UUID reporterAccountId, String category) {}

  private record SafetyUpdateEvent(UUID incidentId, UUID rideId) {}

  private record SafetyAudit(UUID rideId, String category, String state) {}

  private record EvidenceAudit(UUID evidenceId, String mediaType) {}

  private record ActionAudit(String action, String state, String severity) {}
}

package in.rsh.cab.audit;

import in.rsh.cab.audit.internal.persistence.AuditRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import in.rsh.cab.web.RequestMetadata;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class AuditService {

  private final AuditRepository events;
  private final Clock clock;

  public AuditService(AuditRepository events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public UUID record(
      UUID tenantId, UUID actorAccountId, String action, String targetType, UUID targetId,
      String outcome, JsonNode redactedSummary) {
    UUID id = UUID.randomUUID();
    events.insert(
        id, tenantId, actorAccountId, action, targetType, targetId, outcome,
        RequestMetadata.correlationIdOrNull(), redactedSummary, clock.instant());
    return id;
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> list(int limit) {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.TENANT_ADMIN)
        && !context.roles().contains(TenantRole.SUPPORT)) {
      throw new TenantAccessDeniedException("TENANT_ADMIN or SUPPORT role is required");
    }
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("Audit limit must be between 1 and 200");
    }
    return events.findLatest(context.tenantId(), limit);
  }
}

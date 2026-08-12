package in.rsh.cab.geography;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.geography.internal.persistence.ServiceAreaRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServiceAreaService {

  private final ServiceAreaRepository serviceAreas;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ServiceAreaService(ServiceAreaRepository serviceAreas, ObjectMapper objectMapper, Clock clock) {
    this.serviceAreas = serviceAreas;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public ServiceArea create(
      String slug, String name, String timezone, JsonNode suppliedBoundary) {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.TENANT_ADMIN)) {
      throw new TenantAccessDeniedException("TENANT_ADMIN role is required");
    }
    validateTimezone(timezone);
    JsonNode boundary = normalizeBoundary(suppliedBoundary);
    if (serviceAreas.existsByTenantIdAndSlugOrName(context.tenantId(), slug, name)) {
      throw new InvalidRequestException("Service area slug or name is already in use");
    }

    Instant now = clock.instant();
    ServiceArea serviceArea =
        new ServiceArea(
            UUID.randomUUID(), slug, name, "ACTIVE", timezone, boundary, now, now);
    try {
      serviceAreas.insert(context.tenantId(), serviceArea);
    } catch (DuplicateKeyException exception) {
      throw new InvalidRequestException("Service area slug or name is already in use");
    } catch (DataIntegrityViolationException exception) {
      throw new InvalidRequestException("Boundary must be a valid Polygon or MultiPolygon");
    }
    return serviceArea;
  }

  @Transactional(readOnly = true)
  public List<ServiceArea> list() {
    return serviceAreas.findAllByTenantId(TenantContext.require().tenantId());
  }

  private JsonNode normalizeBoundary(JsonNode suppliedBoundary) {
    if (suppliedBoundary == null || suppliedBoundary.isNull()) {
      throw new InvalidRequestException("Boundary is required");
    }
    JsonNode boundary = suppliedBoundary;
    if (boundary.isTextual()) {
      try {
        boundary = objectMapper.readTree(boundary.textValue());
      } catch (JacksonException exception) {
        throw new InvalidRequestException("Boundary must be valid GeoJSON");
      }
    }
    String type = boundary.path("type").asText();
    if (!boundary.isObject()
        || !("Polygon".equals(type) || "MultiPolygon".equals(type))
        || !boundary.path("coordinates").isArray()
        || boundary.path("coordinates").size() == 0) {
      throw new InvalidRequestException("Boundary must be a GeoJSON Polygon or MultiPolygon");
    }
    return boundary;
  }

  private void validateTimezone(String timezone) {
    try {
      ZoneId.of(timezone);
    } catch (ZoneRulesException exception) {
      throw new InvalidRequestException("Timezone must be a valid IANA time zone");
    }
  }
}

package in.rsh.cab.geography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.geography.internal.persistence.ServiceAreaRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ServiceAreaServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final String POLYGON =
      "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}";

  private final ServiceAreaRepository repository = mock(ServiceAreaRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private ServiceAreaService service;

  @BeforeEach
  void setUp() {
    service =
        new ServiceAreaService(
            repository,
            objectMapper,
            Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));
    TenantContext.set(
        new TenantContext(
            TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), Set.of(TenantRole.TENANT_ADMIN)));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createsAreaForCurrentTenantFromObjectBoundary() throws Exception {
    JsonNode boundary = objectMapper.readTree(POLYGON);

    ServiceArea created = service.create("central", "Central", "UTC", boundary);

    assertEquals("ACTIVE", created.status());
    assertEquals(Instant.parse("2026-08-08T00:00:00Z"), created.createdAt());
    verify(repository).insert(TENANT_ID, created);
  }

  @Test
  void acceptsGeoJsonStringAndListsOnlyCurrentTenant() throws Exception {
    JsonNode stringBoundary = objectMapper.valueToTree(POLYGON);
    ServiceArea expected =
        new ServiceArea(
            UUID.randomUUID(),
            "central",
            "Central",
            "ACTIVE",
            "UTC",
            objectMapper.readTree(POLYGON),
            Instant.now(),
            Instant.now());
    when(repository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(expected));

    assertEquals("Polygon", service.create("central", "Central", "UTC", stringBoundary).boundary().get("type").asText());
    assertEquals(List.of(expected), service.list());
    verify(repository).findAllByTenantId(TENANT_ID);
  }

  @Test
  void requiresTenantAdminForCreation() throws Exception {
    TenantContext.set(
        new TenantContext(
            TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), Set.of(TenantRole.DRIVER)));

    assertThrows(
        TenantAccessDeniedException.class,
        () -> service.create("central", "Central", "UTC", objectMapper.readTree(POLYGON)));
  }

  @Test
  void rejectsInvalidBoundaryTimezoneAndDuplicates() throws Exception {
    assertThrows(
        InvalidRequestException.class,
        () -> service.create("central", "Central", "Mars/Olympus", objectMapper.readTree(POLYGON)));
    assertThrows(
        InvalidRequestException.class,
        () -> service.create("central", "Central", "UTC", objectMapper.readTree("null")));
    assertThrows(
        InvalidRequestException.class,
        () -> service.create("central", "Central", "UTC", objectMapper.valueToTree("not-json")));
    assertThrows(
        InvalidRequestException.class,
        () ->
            service.create(
                "central",
                "Central",
                "UTC",
                objectMapper.readTree("{\"type\":\"Point\",\"coordinates\":[0,0]}")));

    when(repository.existsByTenantIdAndSlugOrName(TENANT_ID, "central", "Central"))
        .thenReturn(true);
    assertThrows(
        InvalidRequestException.class,
        () -> service.create("central", "Central", "UTC", objectMapper.readTree(POLYGON)));
  }

  @Test
  void convertsPersistenceConstraintFailuresToSafeValidationErrors() throws Exception {
    org.mockito.Mockito.doThrow(new DuplicateKeyException("secret sql"))
        .when(repository)
        .insert(any(), any());
    InvalidRequestException duplicate =
        assertThrows(
            InvalidRequestException.class,
            () -> service.create("central", "Central", "UTC", objectMapper.readTree(POLYGON)));
    assertEquals("Service area slug or name is already in use", duplicate.getMessage());

    org.mockito.Mockito.reset(repository);
    org.mockito.Mockito.doThrow(new DataIntegrityViolationException("secret sql"))
        .when(repository)
        .insert(any(), any());
    InvalidRequestException invalidGeometry =
        assertThrows(
            InvalidRequestException.class,
            () -> service.create("central", "Central", "UTC", objectMapper.readTree(POLYGON)));
    assertEquals("Boundary must be a valid Polygon or MultiPolygon", invalidGeometry.getMessage());
  }
}

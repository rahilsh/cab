package in.rsh.cab.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import in.rsh.cab.operations.InboxService;
import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.operations.OutboxPoller;
import in.rsh.cab.payment.FakePaymentProvider;
import in.rsh.cab.payment.PaymentAccount;
import in.rsh.cab.payment.PaymentOperationWorker;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import in.rsh.cab.tenancy.TenantExecution;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MarketplaceHttpIT.TestJwtConfiguration.class)
class MarketplaceHttpIT {

  private static final HttpServer OSRM = startOsrm();
  private static final ObjectMapper JSON = new ObjectMapper();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("cab")
          .withUsername("cab")
          .withPassword("cab")
          .withInitScript("postgres/init-app-role.sql");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;
  @Autowired private OutboxPoller outboxPoller;
  @Autowired private InboxService inbox;
  @Autowired private PaymentOperationWorker paymentWorker;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private FakePaymentProvider fakePaymentProvider;
  @Autowired private TenantExecution tenantExecution;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "cab_app");
    registry.add("spring.datasource.password", () -> "cab-app-test");
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("springdoc.api-docs.enabled", () -> "true");
    registry.add("springdoc.swagger-ui.enabled", () -> "true");
    registry.add("rate-limit.requests", () -> "500");
    registry.add("rate-limit.pre-tenant-requests", () -> "2000");
    registry.add("rate-limit.callback-requests", () -> "100");
    registry.add("request-limits.max-bytes", () -> "4096");
    registry.add("request-limits.callback-max-bytes", () -> "2048");
    registry.add("outbox.dispatcher.enabled", () -> "false");
    registry.add("routing.osrm.base-url", () -> "http://localhost:" + OSRM.getAddress().getPort());
  }

  @AfterAll
  static void stopOsrm() {
    OSRM.stop(0);
  }

  @Test
  void booksCabThroughActualHttpApi() throws Exception {
    HttpResponse<String> health = get("/actuator/health/readiness");
    assertEquals(200, health.statusCode());
    assertTrue(health.body().contains("UP"));
    assertTrue(health.headers().firstValue("X-Content-Type-Options").isPresent());

    HttpResponse<String> openApiResponse = get("/v3/api-docs");
    assertEquals(200, openApiResponse.statusCode(), openApiResponse.body());
    JsonNode openApi = JSON.readTree(openApiResponse.body());
    JsonNode paths = openApi.get("paths");
    assertTrue(paths.has("/api/v1/tenants"));
    assertTrue(paths.has("/api/v1/rides"));
    assertTrue(paths.has("/api/v1/rides/{id}/events"));
    assertTrue(paths.has("/api/v1/drivers/me/documents"));
    assertTrue(paths.has("/api/v1/payment-providers/{provider}/accounts/{accountId}/events"));
    paths.propertyNames().forEach(path -> assertTrue(path.startsWith("/api/v1/"), path));
    assertTrue(openApi.get("components").get("securitySchemes").has("bearerAuth"));
    JsonNode createRide = paths.get("/api/v1/rides").get("post");
    assertTrue(createRide.get("security").size() > 0);
    assertTrue(hasHeader(createRide, "X-Tenant-ID"));
    assertTrue(hasHeader(createRide, "Idempotency-Key"));

    HttpResponse<String> unauthorized = postWithoutToken("/api/v1/tenants", "{}");
    assertEquals(401, unauthorized.statusCode());
    assertTrue(
        MediaType.parseMediaType(unauthorized.headers().firstValue("Content-Type").orElseThrow())
            .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    JsonNode unauthorizedProblem = JSON.readTree(unauthorized.body());
    assertEquals("unauthorized", unauthorizedProblem.get("code").asText());
    assertEquals(401, unauthorizedProblem.get("status").asInt());
    assertEquals(401, get("/actuator/prometheus").statusCode());
    assertEquals(200, getWithBearer("/actuator/prometheus", "observability").statusCode());

    HttpResponse<String> invalidTenant = post("/api/v1/tenants", "{\"slug\":\"BAD\"}");
    assertEquals(400, invalidTenant.statusCode());
    assertTrue(invalidTenant.body().contains("validation-failed"));

    HttpResponse<String> created =
        post(
            "/api/v1/tenants",
            "{\"slug\":\"city-cabs\",\"displayName\":\"City Cabs\",\"defaultCurrency\":\"USD\",\"timezone\":\"UTC\"}");
    assertEquals(201, created.statusCode());
    JsonNode tenant = JSON.readTree(created.body());
    assertEquals("city-cabs", tenant.get("slug").asText());
    String tenantId = tenant.get("id").asText();

    HttpResponse<String> listed = getWithToken("/api/v1/tenants");
    assertEquals(200, listed.statusCode());
    JsonNode tenants = JSON.readTree(listed.body());
    assertEquals(1, tenants.size());

    assertEquals(400, getWithToken("/api/v1/current-tenant").statusCode());
    HttpResponse<String> current =
        getWithTenant("/api/v1/current-tenant", tenantId, "platform-admin");
    assertEquals(200, current.statusCode());
    assertTrue(current.body().contains(tenantId));
    String accountId = JSON.readTree(current.body()).get("accountId").asText();
    assertEquals(
        403, getWithTenant("/api/v1/current-tenant", tenantId, "unknown-user").statusCode());

    HttpResponse<String> invitation =
        postWithTenant(
            "/api/v1/current-tenant/invitations",
            tenantId,
            "{\"email\":\"invited-user@example.com\",\"roles\":[\"RIDER\"]}");
    assertEquals(201, invitation.statusCode(), invitation.body());
    JsonNode invitationBody = JSON.readTree(invitation.body());
    String invitationToken = invitationBody.get("token").asText();
    assertFalse(invitationToken.isBlank());
    assertFalse(
        jdbc.sql("SELECT token_hash FROM tenant_membership_invitations WHERE id = :id")
            .param("id", UUID.fromString(invitationBody.get("id").asText()))
            .query(String.class)
            .single()
            .contains(invitationToken));
    HttpResponse<String> invitationAccepted =
        postWithBearer(
            "/api/v1/tenant-invitations/accept",
            "invited-user",
            "{\"token\":\"" + invitationToken + "\"}");
    assertEquals(200, invitationAccepted.statusCode(), invitationAccepted.body());
    JsonNode acceptedMembership = JSON.readTree(invitationAccepted.body());
    assertEquals(tenantId, acceptedMembership.get("tenantId").asText());
    assertEquals(
        200, getWithTenant("/api/v1/current-tenant", tenantId, "invited-user").statusCode());
    assertEquals(
        400,
        postWithTenantBearer("/api/v1/current-tenant/roles/DRIVER", tenantId, "invited-user", "")
            .statusCode());
    assertEquals(
        403,
        postWithBearer(
                "/api/v1/tenant-invitations/accept",
                "different-user",
                "{\"token\":\"" + invitationToken + "\"}")
            .statusCode());

    HttpResponse<String> secondTenant =
        post(
            "/api/v1/tenants",
            "{\"slug\":\"second-city\",\"displayName\":\"Second City\",\"defaultCurrency\":\"USD\",\"timezone\":\"UTC\"}");
    assertEquals(201, secondTenant.statusCode(), secondTenant.body());
    String secondTenantId = JSON.readTree(secondTenant.body()).get("id").asText();
    HttpResponse<String> added =
        postWithTenant(
            "/api/v1/current-tenant/memberships",
            secondTenantId,
            "{\"accountId\":\""
                + acceptedMembership.get("accountId").asText()
                + "\",\"roles\":[\"SUPPORT\"]}");
    assertEquals(201, added.statusCode(), added.body());
    assertEquals(
        200, getWithTenant("/api/v1/current-tenant", secondTenantId, "invited-user").statusCode());

    HttpResponse<String> oversized =
        postWithBearer("/api/v1/tenants", "oversized-user", "x".repeat(4097));
    assertEquals(413, oversized.statusCode(), oversized.body());
    assertEquals("payload-too-large", JSON.readTree(oversized.body()).get("code").asText());

    assertEquals(
        204, postWithTenant("/api/v1/current-tenant/roles/RIDER", tenantId, "").statusCode());
    HttpResponse<String> riderCreated =
        postWithTenant(
            "/api/v1/rider/profile",
            tenantId,
            "{\"displayName\":\"Admin Rider\",\"phoneNumber\":\"+1 555 0100\"}");
    assertEquals(201, riderCreated.statusCode());
    assertEquals("Admin Rider", JSON.readTree(riderCreated.body()).get("displayName").asText());
    assertEquals(
        200, getWithTenant("/api/v1/rider/profile", tenantId, "platform-admin").statusCode());

    HttpResponse<String> vehicleCreated =
        postWithTenant(
            "/api/v1/vehicles",
            tenantId,
            "{\"registration\":\"KA 01 AB 1234\",\"serviceClass\":\"STANDARD\",\"capacity\":4}");
    assertEquals(201, vehicleCreated.statusCode());
    JsonNode vehicle = JSON.readTree(vehicleCreated.body());
    String vehicleId = vehicle.get("id").asText();
    assertEquals("KA01AB1234", vehicle.get("registration").asText());
    assertEquals(
        1,
        JSON.readTree(getWithTenant("/api/v1/vehicles", tenantId, "platform-admin").body()).size());

    HttpResponse<String> driverCreated =
        postWithTenant(
            "/api/v1/drivers",
            tenantId,
            "{\"accountId\":\""
                + accountId
                + "\",\"legalName\":\"Admin Driver\",\"phoneNumber\":\"+1 555 0101\"}");
    assertEquals(201, driverCreated.statusCode());
    String driverId = JSON.readTree(driverCreated.body()).get("id").asText();
    HttpResponse<String> expiredDocument =
        postWithTenant(
            "/api/v1/drivers/me/documents",
            tenantId,
            "{\"documentType\":\"DRIVING_LICENSE\",\"documentReference\":\"license-42\","
                + "\"objectKey\":\"drivers/"
                + driverId
                + "/expired-license.pdf\",\"expiresOn\":\"2020-08-08\"}");
    assertEquals(400, expiredDocument.statusCode(), expiredDocument.body());
    assertEquals(
        409, postWithTenant("/api/v1/drivers/" + driverId + "/approve", tenantId, "").statusCode());
    String validExpiry = LocalDate.now().plusYears(2).toString();
    HttpResponse<String> validDocumentCreated =
        postWithTenant(
            "/api/v1/drivers/me/documents",
            tenantId,
            "{\"documentType\":\"DRIVING_LICENSE\",\"documentReference\":\"license-43\","
                + "\"objectKey\":\"drivers/"
                + driverId
                + "/license.pdf\",\"expiresOn\":\""
                + validExpiry
                + "\"}");
    assertEquals(201, validDocumentCreated.statusCode(), validDocumentCreated.body());
    String validDocumentId = JSON.readTree(validDocumentCreated.body()).get("id").asText();
    assertEquals(
        1,
        JSON.readTree(
                getWithTenant(
                        "/api/v1/drivers/" + driverId + "/documents", tenantId, "platform-admin")
                    .body())
            .size());
    assertEquals(
        200,
        postWithTenant(
                "/api/v1/drivers/" + driverId + "/documents/" + validDocumentId + "/verify",
                tenantId,
                "")
            .statusCode());
    assertEquals(
        200, postWithTenant("/api/v1/drivers/" + driverId + "/approve", tenantId, "").statusCode());
    assertEquals(
        1,
        JSON.readTree(getWithTenant("/api/v1/drivers", tenantId, "platform-admin").body()).size());

    HttpResponse<String> shiftCreated =
        postWithTenant(
            "/api/v1/driver/shifts",
            tenantId,
            "{\"driverId\":\"" + driverId + "\",\"vehicleId\":\"" + vehicleId + "\"}");
    assertEquals(201, shiftCreated.statusCode());
    JsonNode shift = JSON.readTree(shiftCreated.body());
    String shiftId = shift.get("id").asText();
    assertEquals("OFFLINE", shift.get("status").asText());
    HttpResponse<String> online =
        postWithTenant(
            "/api/v1/driver/shifts/" + shiftId + "/go-online", tenantId, "{\"version\":0}");
    assertEquals(200, online.statusCode());
    assertEquals("AVAILABLE", JSON.readTree(online.body()).get("status").asText());
    HttpResponse<String> staleOnline =
        postWithTenant(
            "/api/v1/driver/shifts/" + shiftId + "/go-online", tenantId, "{\"version\":0}");
    assertEquals(409, staleOnline.statusCode());
    assertTrue(staleOnline.body().contains("resource-conflict"));
    HttpResponse<String> offline =
        postWithTenant(
            "/api/v1/driver/shifts/" + shiftId + "/go-offline", tenantId, "{\"version\":1}");
    assertEquals(200, offline.statusCode());
    assertEquals("OFFLINE", JSON.readTree(offline.body()).get("status").asText());

    String boundary =
        "{\"type\":\"Polygon\",\"coordinates\":[[[77.5,12.9],[77.7,12.9],[77.7,13.1],[77.5,12.9]]]}";
    HttpResponse<String> serviceAreaCreated =
        postWithTenant(
            "/api/v1/service-areas",
            tenantId,
            "{\"slug\":\"central\",\"name\":\"Central\",\"timezone\":\"Asia/Kolkata\",\"boundary\":"
                + boundary
                + "}");
    assertEquals(201, serviceAreaCreated.statusCode());
    JsonNode serviceArea = JSON.readTree(serviceAreaCreated.body());
    assertEquals("central", serviceArea.get("slug").asText());
    assertEquals("Polygon", serviceArea.get("boundary").get("type").asText());

    HttpResponse<String> serviceAreas =
        getWithTenant("/api/v1/service-areas", tenantId, "platform-admin");
    assertEquals(200, serviceAreas.statusCode());
    JsonNode serviceAreaList = JSON.readTree(serviceAreas.body());
    assertEquals(1, serviceAreaList.size());
    assertEquals("MultiPolygon", serviceAreaList.get(0).get("boundary").get("type").asText());

    HttpResponse<String> productCreated =
        postWithTenant(
            "/api/v1/products",
            tenantId,
            "{\"slug\":\"standard\",\"name\":\"Standard\",\"status\":\"ACTIVE\","
                + "\"capacity\":4,\"serviceClass\":\"STANDARD\"}");
    assertEquals(201, productCreated.statusCode());
    String productId = JSON.readTree(productCreated.body()).get("id").asText();
    assertEquals(
        409,
        postWithTenant(
                "/api/v1/products",
                tenantId,
                "{\"slug\":\"standard\",\"name\":\"Standard\",\"status\":\"ACTIVE\","
                    + "\"capacity\":4,\"serviceClass\":\"STANDARD\"}")
            .statusCode());
    assertEquals(
        1,
        JSON.readTree(getWithTenant("/api/v1/products", tenantId, "platform-admin").body()).size());

    HttpResponse<String> ruleCreated =
        postWithTenant(
            "/api/v1/pricing-rules",
            tenantId,
            "{\"productId\":\""
                + productId
                + "\",\"effectiveFrom\":\"2020-01-01T00:00:00Z\","
                + "\"baseFareMinor\":200,\"perKmMinor\":100,\"perMinuteMinor\":20,"
                + "\"minimumFareMinor\":700,\"currency\":\"USD\","
                + "\"surgeBasisPoints\":1000,\"taxBasisPoints\":500,\"active\":true}");
    assertEquals(201, ruleCreated.statusCode());
    assertEquals(
        1,
        JSON.readTree(getWithTenant("/api/v1/pricing-rules", tenantId, "platform-admin").body())
            .size());

    String quoteBody =
        "{\"productId\":\""
            + productId
            + "\","
            + "\"pickup\":{\"latitude\":12.95,\"longitude\":77.6},"
            + "\"dropoff\":{\"latitude\":13.0,\"longitude\":77.65}}";
    HttpResponse<String> quoteCreated =
        postWithTenantAndIdempotency("/api/v1/quotes", tenantId, "quote-key-1", quoteBody);
    assertEquals(201, quoteCreated.statusCode());
    JsonNode quote = JSON.readTree(quoteCreated.body());
    assertEquals(809, quote.get("totalMinor").asLong());
    assertEquals("USD", quote.get("currency").asText());
    assertEquals("ACTIVE", quote.get("status").asText());
    String quoteId = quote.get("id").asText();
    HttpResponse<String> quoteReplay =
        postWithTenantAndIdempotency("/api/v1/quotes", tenantId, "quote-key-1", quoteBody);
    assertEquals(201, quoteReplay.statusCode());
    assertEquals(quoteCreated.body(), quoteReplay.body());
    HttpResponse<String> quoteConflict =
        postWithTenantAndIdempotency(
            "/api/v1/quotes", tenantId, "quote-key-1", quoteBody.replace("13.0", "13.01"));
    assertEquals(409, quoteConflict.statusCode());
    assertTrue(quoteConflict.body().contains("idempotency-key-reused"));
    assertEquals(
        200, getWithTenant("/api/v1/quotes/" + quoteId, tenantId, "platform-admin").statusCode());
    assertEquals(
        1,
        JSON.readTree(getWithTenant("/api/v1/quotes", tenantId, "platform-admin").body()).size());
    assertEquals(
        404,
        getWithTenant(
                "/api/v1/quotes/00000000-0000-0000-0000-000000000000", tenantId, "platform-admin")
            .statusCode());

    HttpResponse<String> auditEvents =
        getWithTenant("/api/v1/admin/audit-events", tenantId, "platform-admin");
    assertEquals(200, auditEvents.statusCode());
    JsonNode auditList = JSON.readTree(auditEvents.body());
    assertEquals(1, auditList.size());
    JsonNode audit = auditList.get(0);
    assertEquals("fare_quote.create", audit.get("action").asText());
    assertEquals(quoteId, audit.get("targetId").asText());
    assertEquals("marketplace-http-it", audit.get("correlationId").asText());

    UUID tenantUuid = UUID.fromString(tenantId);
    List<OutboxEvent> leased = outboxPoller.lease(tenantUuid, 10, Duration.ofSeconds(30));
    assertEquals(1, leased.size());
    assertEquals("marketplace-http-it", leased.get(0).correlationId());
    outboxPoller.retry(leased.get(0), Instant.now().minusSeconds(1), "temporary");
    List<OutboxEvent> retried = outboxPoller.lease(tenantUuid, 10, Duration.ofSeconds(30));
    assertEquals(2, retried.get(0).attempts());
    outboxPoller.published(retried.get(0));
    assertTrue(outboxPoller.lease(tenantUuid, 10, Duration.ofSeconds(30)).isEmpty());

    UUID incomingEvent = UUID.randomUUID();
    assertTrue(
        tenantExecution.inTransaction(
            tenantUuid, () -> inbox.receive(tenantUuid, "marketplace-http-it", incomingEvent)));
    assertFalse(
        tenantExecution.inTransaction(
            tenantUuid, () -> inbox.receive(tenantUuid, "marketplace-http-it", incomingEvent)));
    UUID auditId = UUID.fromString(audit.get("id").asText());
    assertThrows(
        DataAccessException.class,
        () ->
            tenantExecution.inTransaction(
                tenantUuid,
                () ->
                    jdbc.sql(
                            "UPDATE audit_events SET action = 'changed' WHERE tenant_id = :tenantId AND id = :id")
                        .param("tenantId", tenantUuid)
                        .param("id", auditId)
                        .update()));

    HttpResponse<String> route =
        postWithTenant(
            "/api/v1/routes/estimate",
            tenantId,
            "{\"origin\":{\"latitude\":12.9,\"longitude\":77.5},\"destination\":{\"latitude\":13.0,\"longitude\":77.6}}");
    assertEquals(200, route.statusCode());
    JsonNode estimate = JSON.readTree(route.body());
    assertEquals(2450.5, estimate.get("distanceMeters").asDouble());
    assertEquals(480.0, estimate.get("durationSeconds").asDouble());

    HttpResponse<String> onlineForDispatch =
        postWithTenant(
            "/api/v1/driver/shifts/" + shiftId + "/go-online", tenantId, "{\"version\":2}");
    assertEquals(200, onlineForDispatch.statusCode());
    String recordedAt = Instant.now().toString();
    String locationBody =
        "{\"shiftId\":\""
            + shiftId
            + "\",\"latitude\":12.95,\"longitude\":77.6,\"recordedAt\":\""
            + recordedAt
            + "\",\"sequence\":1}";
    assertEquals(
        200, putWithTenant("/api/v1/driver/location", tenantId, locationBody).statusCode());
    assertEquals(
        409, putWithTenant("/api/v1/driver/location", tenantId, locationBody).statusCode());

    HttpResponse<String> rideCreated =
        postWithTenantAndIdempotency(
            "/api/v1/rides", tenantId, "ride-key-1", "{\"quoteId\":\"" + quoteId + "\"}");
    assertEquals(201, rideCreated.statusCode(), rideCreated.body());
    JsonNode ride = JSON.readTree(rideCreated.body());
    String rideId = ride.get("id").asText();
    assertEquals("REQUESTED", ride.get("status").asText());
    assertEquals(
        rideCreated.body(),
        postWithTenantAndIdempotency(
                "/api/v1/rides", tenantId, "ride-key-1", "{\"quoteId\":\"" + quoteId + "\"}")
            .body());
    assertEquals(
        1, JSON.readTree(getWithTenant("/api/v1/rides", tenantId, "platform-admin").body()).size());
    assertEquals(
        404,
        httpClient
            .send(
                tenantGetRequest(
                    "/api/v1/rides/" + UUID.randomUUID() + "/events",
                    tenantId,
                    "platform-admin",
                    MediaType.TEXT_EVENT_STREAM_VALUE),
                HttpResponse.BodyHandlers.ofString())
            .statusCode());
    assertEquals(
        403,
        httpClient
            .send(
                tenantGetRequest(
                    "/api/v1/rides/" + rideId + "/events",
                    tenantId,
                    "unknown-user",
                    MediaType.TEXT_EVENT_STREAM_VALUE),
                HttpResponse.BodyHandlers.ofString())
            .statusCode());
    HttpRequest eventRequest =
        HttpRequest.newBuilder(uri("/api/v1/rides/" + rideId + "/events"))
            .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
            .header("Authorization", "Bearer platform-admin")
            .header("X-Tenant-ID", tenantId)
            .GET()
            .build();
    CompletableFuture<HttpResponse<java.util.stream.Stream<String>>> eventResponse =
        httpClient.sendAsync(eventRequest, HttpResponse.BodyHandlers.ofLines());
    HttpResponse<java.util.stream.Stream<String>> streamResponse =
        eventResponse.get(5, TimeUnit.SECONDS);
    assertEquals(200, streamResponse.statusCode());

    HttpResponse<String> offersCreated =
        postWithTenant("/api/v1/dispatch/rides/" + rideId + "/start", tenantId, "{\"version\":0}");
    assertEquals(200, offersCreated.statusCode(), offersCreated.body());
    String statusEvent =
        streamResponse.body().filter(line -> line.contains("MATCHING")).findFirst().orElseThrow();
    assertTrue(statusEvent.contains("rideId"));
    assertFalse(statusEvent.contains("latitude"));
    streamResponse.body().close();
    JsonNode offers = JSON.readTree(offersCreated.body());
    assertEquals(1, offers.size());
    String offerId = offers.get(0).get("id").asText();
    assertEquals(
        1,
        JSON.readTree(getWithTenant("/api/v1/driver/offers", tenantId, "platform-admin").body())
            .size());

    HttpRequest acceptRequest =
        tenantPostRequest("/api/v1/driver/offers/" + offerId + "/accept", tenantId, "");
    CompletableFuture<HttpResponse<String>> firstAccept =
        httpClient.sendAsync(acceptRequest, HttpResponse.BodyHandlers.ofString());
    CompletableFuture<HttpResponse<String>> secondAccept =
        httpClient.sendAsync(acceptRequest, HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> firstResult = firstAccept.join();
    HttpResponse<String> secondResult = secondAccept.join();
    assertEquals(Set.of(200, 409), Set.of(firstResult.statusCode(), secondResult.statusCode()));
    HttpResponse<String> acceptedRide =
        firstResult.statusCode() == 200 ? firstResult : secondResult;
    assertEquals("DRIVER_ASSIGNED", JSON.readTree(acceptedRide.body()).get("status").asText());

    JsonNode arriving = driverRideAction(tenantId, rideId, "arriving", 2);
    assertEquals("DRIVER_ARRIVING", arriving.get("status").asText());
    JsonNode arrived = driverRideAction(tenantId, rideId, "arrive", 3);
    assertEquals("DRIVER_ARRIVED", arrived.get("status").asText());
    JsonNode started = driverRideAction(tenantId, rideId, "start", 4);
    assertEquals("IN_PROGRESS", started.get("status").asText());
    JsonNode completed = driverRideAction(tenantId, rideId, "complete", 5);
    assertEquals("COMPLETED", completed.get("status").asText());
    assertEquals(
        409,
        postWithTenant("/api/v1/driver/rides/" + rideId + "/complete", tenantId, "{\"version\":5}")
            .statusCode());

    HttpResponse<String> rating =
        postWithTenant(
            "/api/v1/rides/" + rideId + "/ratings",
            tenantId,
            "{\"score\":5,\"comment\":\"Safe and professional\"}");
    assertEquals(201, rating.statusCode(), rating.body());
    JsonNode ratingBody = JSON.readTree(rating.body());
    assertEquals(5, ratingBody.get("score").asInt());
    assertEquals(
        200,
        getWithTenant(
                "/api/v1/ratings/" + ratingBody.get("id").asText(), tenantId, "platform-admin")
            .statusCode());
    assertEquals(
        409,
        postWithTenant("/api/v1/rides/" + rideId + "/ratings", tenantId, "{\"score\":4}")
            .statusCode());

    HttpResponse<String> supportCase =
        postWithTenant(
            "/api/v1/support/cases",
            tenantId,
            "{\"rideId\":\""
                + rideId
                + "\",\"subject\":\"Receipt question\","
                + "\"message\":\"Please explain the receipt\"}");
    assertEquals(201, supportCase.statusCode(), supportCase.body());
    JsonNode supportCaseBody = JSON.readTree(supportCase.body());
    String supportCaseId = supportCaseBody.get("id").asText();
    JsonNode supportCases =
        JSON.readTree(getWithTenant("/api/v1/support/cases", tenantId, "platform-admin").body());
    assertEquals(1, supportCases.size());
    assertEquals(1, supportCases.get(0).get("messages").size());
    assertEquals(
        200,
        getWithTenant("/api/v1/support/cases/" + supportCaseId, tenantId, "platform-admin")
            .statusCode());
    assertEquals(
        400,
        postWithTenant(
                "/api/v1/support/cases/" + supportCaseId + "/messages",
                tenantId,
                "{\"body\":\"missing version\",\"internal\":false}")
            .statusCode());
    assertEquals(
        200,
        postWithTenant(
                "/api/v1/support/cases/" + supportCaseId + "/messages",
                tenantId,
                "{\"version\":0,\"body\":\"We are reviewing this\",\"internal\":true}")
            .statusCode());
    assertEquals(
        200,
        postWithTenant(
                "/api/v1/support/cases/" + supportCaseId + "/state",
                tenantId,
                "{\"expectedState\":\"OPEN\",\"state\":\"CLOSED\",\"version\":1}")
            .statusCode());
    assertEquals(
        409,
        postWithTenant(
                "/api/v1/support/cases/" + supportCaseId + "/messages",
                tenantId,
                "{\"version\":2,\"body\":\"late\",\"internal\":true}")
            .statusCode());
    assertEquals(
        409,
        postWithTenant(
                "/api/v1/support/cases/" + supportCaseId + "/state",
                tenantId,
                "{\"expectedState\":\"CLOSED\",\"state\":\"IN_PROGRESS\",\"version\":2}")
            .statusCode());

    HttpResponse<String> incident =
        postWithTenant(
            "/api/v1/safety/incidents",
            tenantId,
            "{\"rideId\":\""
                + rideId
                + "\",\"category\":\"UNSAFE_DRIVING\","
                + "\"description\":\"Hard braking near the destination\"}");
    assertEquals(201, incident.statusCode(), incident.body());
    String incidentId = JSON.readTree(incident.body()).get("id").asText();
    HttpResponse<String> evidence =
        postWithTenant(
            "/api/v1/safety/incidents/" + incidentId + "/evidence",
            tenantId,
            "{\"version\":0,\"objectKey\":\"incidents/photo.jpg\","
                + "\"mediaType\":\"image/jpeg\",\"sizeBytes\":10}");
    assertEquals(201, evidence.statusCode(), evidence.body());
    assertEquals(
        1,
        JSON.readTree(getWithTenant("/api/v1/safety/incidents", tenantId, "platform-admin").body())
            .get(0)
            .get("evidence")
            .size());
    assertEquals(
        200,
        getWithTenant("/api/v1/safety/incidents/" + incidentId, tenantId, "platform-admin")
            .statusCode());
    assertEquals(
        200,
        postWithTenant(
                "/api/v1/safety/incidents/" + incidentId + "/actions",
                tenantId,
                "{\"action\":\"CLOSE\",\"expectedState\":\"REPORTED\","
                    + "\"state\":\"CLOSED\",\"severity\":\"HIGH\",\"version\":1}")
            .statusCode());
    assertEquals(
        409,
        postWithTenant(
                "/api/v1/safety/incidents/" + incidentId + "/evidence",
                tenantId,
                "{\"version\":2,\"objectKey\":\"incidents/late.jpg\","
                    + "\"mediaType\":\"image/jpeg\",\"sizeBytes\":10}")
            .statusCode());
    assertEquals(
        409,
        postWithTenant(
                "/api/v1/safety/incidents/" + incidentId + "/actions",
                tenantId,
                "{\"action\":\"REOPEN\",\"expectedState\":\"CLOSED\","
                    + "\"state\":\"INVESTIGATING\",\"severity\":\"HIGH\",\"version\":2}")
            .statusCode());

    HttpResponse<String> preference =
        putWithTenant(
            "/api/v1/notification-preferences",
            tenantId,
            "{\"eventType\":\"rating.created\",\"channel\":\"LOCAL\",\"enabled\":false}");
    assertEquals(200, preference.statusCode(), preference.body());
    assertFalse(JSON.readTree(preference.body()).get("enabled").asBoolean());

    HttpResponse<String> unsafeWebhook =
        postWithTenant(
            "/api/v1/admin/webhook-subscriptions",
            tenantId,
            "{\"url\":\"https://localhost/hook\",\"secretReference\":\"env:CAB_WEBHOOK_SECRET\","
                + "\"eventFilters\":[\"ride.completed\"],\"enabled\":true,\"version\":0}");
    assertEquals(400, unsafeWebhook.statusCode(), unsafeWebhook.body());
    assertTrue(unsafeWebhook.body().contains("non-public"));

    HttpResponse<String> pendingPaymentResponse =
        getWithTenant("/api/v1/rides/" + rideId + "/payment", tenantId, "platform-admin");
    assertEquals(200, pendingPaymentResponse.statusCode(), pendingPaymentResponse.body());
    JsonNode pendingPayment = JSON.readTree(pendingPaymentResponse.body());
    String paymentId = pendingPayment.get("id").asText();
    assertEquals("CAPTURE_PENDING", pendingPayment.get("state").asText());
    assertEquals(809, pendingPayment.get("amountMinor").asLong());

    List<OutboxEvent> paymentEvents = outboxPoller.lease(tenantUuid, 100, Duration.ofSeconds(30));
    OutboxEvent captureRequest =
        paymentEvents.stream()
            .filter(event -> event.eventType().equals("payment.capture_requested"))
            .findFirst()
            .orElseThrow();
    assertTrue(paymentWorker.process(captureRequest));
    paymentEvents.forEach(outboxPoller::published);

    PaymentAccount paymentAccount =
        tenantExecution.inTransaction(
            tenantUuid,
            () ->
                paymentRepository
                    .findAccountForPayment(tenantUuid, UUID.fromString(paymentId))
                    .orElseThrow());
    Instant captureTimestamp = Instant.now();
    String captureBody =
        "{\"eventId\":\"capture-event-1\",\"type\":\"CAPTURE_SUCCEEDED\","
            + "\"paymentId\":\""
            + paymentId
            + "\",\"refundId\":null,"
            + "\"providerObjectId\":\"fake-pay-"
            + paymentId
            + "\",\"providerVersion\":1,"
            + "\"amountMinor\":809,\"currency\":\"USD\",\"failureCode\":null}";
    HttpResponse<String> captureCallback =
        providerCallback(paymentAccount.id(), captureTimestamp, captureBody);
    assertEquals(200, captureCallback.statusCode(), captureCallback.body());
    assertTrue(JSON.readTree(captureCallback.body()).get("applied").asBoolean());
    assertEquals(
        "CAPTURED",
        JSON.readTree(
                getWithTenant("/api/v1/payments/" + paymentId, tenantId, "platform-admin").body())
            .get("state")
            .asText());
    assertEquals(
        688,
        JSON.readTree(getWithTenant("/api/v1/driver/earnings", tenantId, "platform-admin").body())
            .get(0)
            .get("availableMinor")
            .asLong());

    HttpResponse<String> refundResponse =
        postWithTenantAndIdempotency(
            "/api/v1/finance/payments/" + paymentId + "/refunds",
            tenantId,
            "refund-http-it",
            "{\"amountMinor\":200,\"reason\":\"service adjustment\"}");
    assertEquals(201, refundResponse.statusCode(), refundResponse.body());
    assertEquals("false", refundResponse.headers().firstValue("Idempotent-Replayed").orElseThrow());
    String refundId = JSON.readTree(refundResponse.body()).get("id").asText();
    HttpResponse<String> replayedRefund =
        postWithTenantAndIdempotency(
            "/api/v1/finance/payments/" + paymentId + "/refunds",
            tenantId,
            "refund-http-it",
            "{\"amountMinor\":200,\"reason\":\"service adjustment\"}");
    assertEquals(201, replayedRefund.statusCode(), replayedRefund.body());
    assertEquals(refundResponse.body(), replayedRefund.body());
    assertEquals("true", replayedRefund.headers().firstValue("Idempotent-Replayed").orElseThrow());
    assertEquals(
        409,
        postWithTenantAndIdempotency(
                "/api/v1/finance/payments/" + paymentId + "/refunds",
                tenantId,
                "refund-http-it",
                "{\"amountMinor\":201,\"reason\":\"service adjustment\"}")
            .statusCode());
    List<OutboxEvent> refundEvents = outboxPoller.lease(tenantUuid, 100, Duration.ofSeconds(30));
    OutboxEvent refundRequest =
        refundEvents.stream()
            .filter(event -> event.eventType().equals("payment.refund_requested"))
            .findFirst()
            .orElseThrow();
    assertTrue(paymentWorker.process(refundRequest));
    refundEvents.forEach(outboxPoller::published);

    Instant refundTimestamp = Instant.now();
    String refundBody =
        "{\"eventId\":\"refund-event-1\",\"type\":\"REFUND_SUCCEEDED\","
            + "\"paymentId\":\""
            + paymentId
            + "\",\"refundId\":\""
            + refundId
            + "\","
            + "\"providerObjectId\":\"fake-refund-"
            + refundId
            + "\",\"providerVersion\":1,"
            + "\"amountMinor\":200,\"currency\":\"USD\",\"failureCode\":null}";
    assertEquals(
        200, providerCallback(paymentAccount.id(), refundTimestamp, refundBody).statusCode());
    JsonNode refunded =
        JSON.readTree(
            getWithTenant("/api/v1/finance/refunds/" + refundId, tenantId, "platform-admin")
                .body());
    assertEquals("SUCCEEDED", refunded.get("state").asText());
    assertEquals(
        518,
        JSON.readTree(getWithTenant("/api/v1/driver/earnings", tenantId, "platform-admin").body())
            .get(0)
            .get("availableMinor")
            .asLong());

    jdbc.sql(
            """
            INSERT INTO tenant_membership_roles (membership_id, role)
            SELECT id, 'FINANCE' FROM tenant_memberships
            WHERE tenant_id = :tenantId AND user_account_id = :accountId
            ON CONFLICT DO NOTHING
            """)
        .param("tenantId", UUID.fromString(tenantId))
        .param("accountId", UUID.fromString(accountId))
        .update();
    HttpResponse<String> settlementResponse =
        postWithTenant("/api/v1/finance/settlements", tenantId, "{\"currency\":\"USD\"}");
    assertEquals(201, settlementResponse.statusCode(), settlementResponse.body());
    JsonNode settlement = JSON.readTree(settlementResponse.body());
    assertEquals("PROCESSING", settlement.get("state").asText());
    String payoutId = settlement.get("payouts").get(0).get("id").asText();
    List<OutboxEvent> payoutEvents = outboxPoller.lease(tenantUuid, 100, Duration.ofSeconds(30));
    OutboxEvent payoutRequest =
        payoutEvents.stream()
            .filter(event -> event.eventType().equals("payout.requested"))
            .findFirst()
            .orElseThrow();
    assertTrue(paymentWorker.process(payoutRequest));
    payoutEvents.forEach(outboxPoller::published);
    Instant payoutTimestamp = Instant.now();
    String payoutBody =
        "{\"eventId\":\"payout-event-1\",\"type\":\"PAYOUT_SUCCEEDED\","
            + "\"paymentId\":null,\"refundId\":null,\"payoutId\":\""
            + payoutId
            + "\","
            + "\"providerObjectId\":\"fake-payout-"
            + payoutId
            + "\",\"providerVersion\":1,"
            + "\"amountMinor\":518,\"currency\":\"USD\",\"failureCode\":null}";
    assertEquals(
        200, providerCallback(paymentAccount.id(), payoutTimestamp, payoutBody).statusCode());
    JsonNode settlements =
        JSON.readTree(
            getWithTenant("/api/v1/finance/settlements", tenantId, "platform-admin").body());
    assertEquals("COMPLETED", settlements.get(0).get("state").asText());
    assertEquals(payoutId, settlements.get(0).get("payouts").get(0).get("id").asText());
  }

  @Test
  void databaseRoleEnforcesTransactionLocalTenantIsolation() {
    assertEquals(
        43,
        jdbc.sql(
                """
                SELECT count(*)
                FROM pg_class table_definition
                JOIN pg_namespace schema_definition
                  ON schema_definition.oid = table_definition.relnamespace
                WHERE schema_definition.nspname = 'public'
                  AND table_definition.relkind = 'r'
                  AND table_definition.relrowsecurity
                  AND table_definition.relforcerowsecurity
                  AND EXISTS (
                    SELECT 1 FROM pg_policy policy
                    WHERE policy.polrelid = table_definition.oid)
                """)
            .query(Integer.class)
            .single());
    assertEquals(
        "cab_app:false:false",
        jdbc.sql(
                """
                SELECT current_user || ':' || rolsuper || ':' || rolbypassrls
                FROM pg_roles WHERE rolname = current_user
                """)
            .query(String.class)
            .single());

    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    insertTenant(tenantA, "rls-a-" + tenantA.toString().substring(0, 8));
    insertTenant(tenantB, "rls-b-" + tenantB.toString().substring(0, 8));
    insertProduct(tenantA, "a");
    insertProduct(tenantB, "b");

    assertEquals(
        1,
        tenantExecution.inTransaction(
            tenantA,
            () -> jdbc.sql("SELECT count(*) FROM service_products").query(Integer.class).single()));
    assertEquals(
        0, jdbc.sql("SELECT count(*) FROM service_products").query(Integer.class).single());
    assertEquals(
        0,
        tenantExecution.inTransaction(
            tenantA,
            () ->
                jdbc.sql("UPDATE service_products SET name = 'blocked' WHERE tenant_id = :tenant")
                    .param("tenant", tenantB)
                    .update()));
    assertThrows(DataAccessException.class, () -> insertProductAs(tenantA, tenantB, "blocked"));
  }

  private void insertTenant(UUID tenantId, String slug) {
    jdbc.sql(
            """
            INSERT INTO tenants
              (id, slug, display_name, status, default_currency, timezone, created_at, updated_at)
            VALUES (:id, :slug, :name, 'ACTIVE', 'USD', 'UTC', now(), now())
            """)
        .param("id", tenantId)
        .param("slug", slug)
        .param("name", slug)
        .update();
  }

  private void insertProduct(UUID tenantId, String slug) {
    tenantExecution.inTransaction(tenantId, () -> insertProductAs(tenantId, tenantId, slug));
  }

  private void insertProductAs(UUID contextTenant, UUID rowTenant, String slug) {
    tenantExecution.inTransaction(
        contextTenant,
        () ->
            jdbc.sql(
                    """
                    INSERT INTO service_products
                      (id, tenant_id, slug, name, status, capacity, service_class, created_at, updated_at)
                    VALUES (:id, :tenant, :slug, :name, 'ACTIVE', 4, 'STANDARD', now(), now())
                    """)
                .param("id", UUID.randomUUID())
                .param("tenant", rowTenant)
                .param("slug", slug)
                .param("name", "Product " + slug)
                .update());
  }

  @Test
  void rateLimitsTenantActorInRedisWithoutLimitingHealth() throws Exception {
    String slug = "limited-" + UUID.randomUUID().toString().substring(0, 8);
    HttpResponse<String> created =
        postWithBearer(
            "/api/v1/tenants",
            "limited-actor",
            "{\"slug\":\""
                + slug
                + "\",\"displayName\":\"Limited Tenant\",\"defaultCurrency\":\"USD\",\"timezone\":\"UTC\"}");
    assertEquals(201, created.statusCode(), created.body());
    String tenantId = JSON.readTree(created.body()).get("id").asText();

    HttpResponse<String> response = null;
    for (int request = 0; request <= 500; request++) {
      response = getWithTenant("/api/v1/current-tenant", tenantId, "limited-actor");
    }

    assertEquals(429, response.statusCode(), response.body());
    assertTrue(response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(0L) > 0);
    assertEquals("rate-limit-exceeded", JSON.readTree(response.body()).get("code").asText());
    assertEquals(200, get("/actuator/health/readiness").statusCode());
  }

  private boolean hasHeader(JsonNode operation, String name) {
    for (JsonNode parameter : operation.get("parameters")) {
      if (name.equals(parameter.get("name").asText())) {
        return true;
      }
    }
    return false;
  }

  private JsonNode driverRideAction(String tenantId, String rideId, String action, long version)
      throws Exception {
    HttpResponse<String> response =
        postWithTenant(
            "/api/v1/driver/rides/" + rideId + "/" + action,
            tenantId,
            "{\"version\":" + version + "}");
    assertEquals(200, response.statusCode(), response.body());
    return JSON.readTree(response.body());
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    return post(path, body, true);
  }

  private HttpResponse<String> postWithoutToken(String path, String body) throws Exception {
    return post(path, body, false);
  }

  private HttpResponse<String> postWithBearer(String path, String token, String body)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postWithTenant(String path, String tenantId, String body)
      throws Exception {
    return httpClient.send(
        tenantPostRequest(path, tenantId, body), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postWithTenantBearer(
      String path, String tenantId, String token, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer " + token)
            .header("X-Tenant-ID", tenantId)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest tenantPostRequest(String path, String tenantId, String body) {
    return HttpRequest.newBuilder(uri(path))
        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .header("Authorization", "Bearer platform-admin")
        .header("X-Tenant-ID", tenantId)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private HttpResponse<String> postWithTenantAndIdempotency(
      String path, String tenantId, String idempotencyKey, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer platform-admin")
            .header("X-Tenant-ID", tenantId)
            .header("X-Correlation-ID", "marketplace-http-it")
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> putWithTenant(String path, String tenantId, String body)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer platform-admin")
            .header("X-Tenant-ID", tenantId)
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String body, boolean authenticated)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    if (authenticated) {
      builder.header("Authorization", "Bearer platform-admin");
    }
    HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> providerCallback(UUID accountId, Instant timestamp, String body)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                uri("/api/v1/payment-providers/fake/accounts/" + accountId + "/events"))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("X-Provider-Timestamp", Long.toString(timestamp.getEpochSecond()))
            .header("X-Provider-Signature", fakePaymentProvider.sign(timestamp, body))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> getWithToken(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer platform-admin")
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> getWithBearer(String path, String token) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.ALL_VALUE)
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> getWithTenant(String path, String tenantId, String token)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer " + token)
            .header("X-Tenant-ID", tenantId)
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest tenantGetRequest(String path, String tenantId, String token, String accept) {
    return HttpRequest.newBuilder(uri(path))
        .header("Accept", accept)
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-ID", tenantId)
        .GET()
        .build();
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static HttpServer startOsrm() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/route/v1/driving/",
          exchange -> {
            byte[] body =
                "{\"code\":\"Ok\",\"routes\":[{\"distance\":2450.5,\"duration\":480.0}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestJwtConfiguration {

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .issuer("https://issuer.example")
              .subject(token)
              .claim(
                  "scope", token.equals("observability") ? "observability.read" : "platform.admin")
              .claim("email", token + "@example.com")
              .claim("email_verified", true)
              .claim("name", "Platform Admin")
              .build();
    }
  }
}

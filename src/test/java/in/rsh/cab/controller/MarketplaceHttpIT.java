package in.rsh.cab.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import in.rsh.cab.operations.InboxService;
import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.operations.OutboxPoller;
import in.rsh.cab.payment.FakePaymentProvider;
import in.rsh.cab.payment.PaymentAccount;
import in.rsh.cab.payment.PaymentOperationWorker;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MarketplaceHttpIT.TestJwtConfiguration.class)
class MarketplaceHttpIT {

  private static final HttpServer OSRM = startOsrm();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:17-3.5")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("cab")
          .withUsername("cab")
          .withPassword("cab");

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

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add(
        "routing.osrm.base-url",
        () -> "http://localhost:" + OSRM.getAddress().getPort());
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

    assertEquals(401, postWithoutToken("/api/v1/tenants", "{}").statusCode());
    assertEquals(403, post("/cities", "{\"name\":\"BLR\"}").statusCode());

    HttpResponse<String> invalidTenant = post("/api/v1/tenants", "{\"slug\":\"BAD\"}");
    assertEquals(400, invalidTenant.statusCode());
    assertTrue(invalidTenant.body().contains("validation-failed"));

    HttpResponse<String> created =
        post(
            "/api/v1/tenants",
            "{\"slug\":\"city-cabs\",\"displayName\":\"City Cabs\",\"defaultCurrency\":\"USD\",\"timezone\":\"UTC\"}");
    assertEquals(201, created.statusCode());
    JsonObject tenant = JsonParser.parseString(created.body()).getAsJsonObject();
    assertEquals("city-cabs", tenant.get("slug").getAsString());
    String tenantId = tenant.get("id").getAsString();

    HttpResponse<String> listed = getWithToken("/api/v1/tenants");
    assertEquals(200, listed.statusCode());
    JsonArray tenants = JsonParser.parseString(listed.body()).getAsJsonArray();
    assertEquals(1, tenants.size());

    assertEquals(400, getWithToken("/api/v1/current-tenant").statusCode());
    HttpResponse<String> current = getWithTenant("/api/v1/current-tenant", tenantId, "platform-admin");
    assertEquals(200, current.statusCode());
    assertTrue(current.body().contains(tenantId));
    String accountId =
        JsonParser.parseString(current.body()).getAsJsonObject().get("accountId").getAsString();
    assertEquals(
        403,
        getWithTenant("/api/v1/current-tenant", tenantId, "unknown-user").statusCode());

    assertEquals(
        204,
        postWithTenant("/api/v1/current-tenant/roles/RIDER", tenantId, "").statusCode());
    HttpResponse<String> riderCreated =
        postWithTenant(
            "/api/v1/rider/profile",
            tenantId,
            "{\"displayName\":\"Admin Rider\",\"phoneNumber\":\"+1 555 0100\"}");
    assertEquals(201, riderCreated.statusCode());
    assertEquals(
        "Admin Rider",
        JsonParser.parseString(riderCreated.body()).getAsJsonObject().get("displayName").getAsString());
    assertEquals(200, getWithTenant("/api/v1/rider/profile", tenantId, "platform-admin").statusCode());

    HttpResponse<String> vehicleCreated =
        postWithTenant(
            "/api/v1/vehicles",
            tenantId,
            "{\"registration\":\"KA 01 AB 1234\",\"serviceClass\":\"STANDARD\",\"capacity\":4}");
    assertEquals(201, vehicleCreated.statusCode());
    JsonObject vehicle = JsonParser.parseString(vehicleCreated.body()).getAsJsonObject();
    String vehicleId = vehicle.get("id").getAsString();
    assertEquals("KA01AB1234", vehicle.get("registration").getAsString());
    assertEquals(1, JsonParser.parseString(
        getWithTenant("/api/v1/vehicles", tenantId, "platform-admin").body()).getAsJsonArray().size());

    HttpResponse<String> driverCreated =
        postWithTenant(
            "/api/v1/drivers",
            tenantId,
            "{\"accountId\":\"" + accountId
                + "\",\"legalName\":\"Admin Driver\",\"phoneNumber\":\"+1 555 0101\"}");
    assertEquals(201, driverCreated.statusCode());
    String driverId =
        JsonParser.parseString(driverCreated.body()).getAsJsonObject().get("id").getAsString();
    assertEquals(
        200,
        postWithTenant("/api/v1/drivers/" + driverId + "/approve", tenantId, "").statusCode());
    assertEquals(1, JsonParser.parseString(
        getWithTenant("/api/v1/drivers", tenantId, "platform-admin").body()).getAsJsonArray().size());

    HttpResponse<String> shiftCreated =
        postWithTenant(
            "/api/v1/driver/shifts",
            tenantId,
            "{\"driverId\":\"" + driverId + "\",\"vehicleId\":\"" + vehicleId + "\"}");
    assertEquals(201, shiftCreated.statusCode());
    JsonObject shift = JsonParser.parseString(shiftCreated.body()).getAsJsonObject();
    String shiftId = shift.get("id").getAsString();
    assertEquals("OFFLINE", shift.get("status").getAsString());
    HttpResponse<String> online = postWithTenant(
        "/api/v1/driver/shifts/" + shiftId + "/go-online", tenantId, "{\"version\":0}");
    assertEquals(200, online.statusCode());
    assertEquals("AVAILABLE",
        JsonParser.parseString(online.body()).getAsJsonObject().get("status").getAsString());
    HttpResponse<String> staleOnline = postWithTenant(
        "/api/v1/driver/shifts/" + shiftId + "/go-online", tenantId, "{\"version\":0}");
    assertEquals(409, staleOnline.statusCode());
    assertTrue(staleOnline.body().contains("resource-conflict"));
    HttpResponse<String> offline = postWithTenant(
        "/api/v1/driver/shifts/" + shiftId + "/go-offline", tenantId, "{\"version\":1}");
    assertEquals(200, offline.statusCode());
    assertEquals("OFFLINE",
        JsonParser.parseString(offline.body()).getAsJsonObject().get("status").getAsString());

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
    JsonObject serviceArea = JsonParser.parseString(serviceAreaCreated.body()).getAsJsonObject();
    assertEquals("central", serviceArea.get("slug").getAsString());
    assertEquals("Polygon", serviceArea.getAsJsonObject("boundary").get("type").getAsString());

    HttpResponse<String> serviceAreas = getWithTenant("/api/v1/service-areas", tenantId, "platform-admin");
    assertEquals(200, serviceAreas.statusCode());
    JsonArray serviceAreaList = JsonParser.parseString(serviceAreas.body()).getAsJsonArray();
    assertEquals(1, serviceAreaList.size());
    assertEquals(
        "MultiPolygon",
        serviceAreaList.get(0).getAsJsonObject().getAsJsonObject("boundary").get("type").getAsString());

    HttpResponse<String> productCreated =
        postWithTenant(
            "/api/v1/products",
            tenantId,
            "{\"slug\":\"standard\",\"name\":\"Standard\",\"status\":\"ACTIVE\","
                + "\"capacity\":4,\"serviceClass\":\"STANDARD\"}");
    assertEquals(201, productCreated.statusCode());
    String productId =
        JsonParser.parseString(productCreated.body()).getAsJsonObject().get("id").getAsString();
    assertEquals(409,
        postWithTenant(
            "/api/v1/products",
            tenantId,
            "{\"slug\":\"standard\",\"name\":\"Standard\",\"status\":\"ACTIVE\","
                + "\"capacity\":4,\"serviceClass\":\"STANDARD\"}").statusCode());
    assertEquals(1, JsonParser.parseString(
        getWithTenant("/api/v1/products", tenantId, "platform-admin").body()).getAsJsonArray().size());

    HttpResponse<String> ruleCreated =
        postWithTenant(
            "/api/v1/pricing-rules",
            tenantId,
            "{\"productId\":\"" + productId + "\",\"effectiveFrom\":\"2020-01-01T00:00:00Z\","
                + "\"baseFareMinor\":200,\"perKmMinor\":100,\"perMinuteMinor\":20,"
                + "\"minimumFareMinor\":700,\"currency\":\"USD\","
                + "\"surgeBasisPoints\":1000,\"taxBasisPoints\":500,\"active\":true}");
    assertEquals(201, ruleCreated.statusCode());
    assertEquals(1, JsonParser.parseString(
        getWithTenant("/api/v1/pricing-rules", tenantId, "platform-admin").body()).getAsJsonArray().size());

    String quoteBody =
        "{\"productId\":\"" + productId + "\","
            + "\"pickup\":{\"latitude\":12.95,\"longitude\":77.6},"
            + "\"dropoff\":{\"latitude\":13.0,\"longitude\":77.65}}";
    HttpResponse<String> quoteCreated =
        postWithTenantAndIdempotency(
            "/api/v1/quotes",
            tenantId,
            "quote-key-1",
            quoteBody);
    assertEquals(201, quoteCreated.statusCode());
    JsonObject quote = JsonParser.parseString(quoteCreated.body()).getAsJsonObject();
    assertEquals(809, quote.get("totalMinor").getAsLong());
    assertEquals("USD", quote.get("currency").getAsString());
    assertEquals("ACTIVE", quote.get("status").getAsString());
    String quoteId = quote.get("id").getAsString();
    HttpResponse<String> quoteReplay = postWithTenantAndIdempotency(
        "/api/v1/quotes", tenantId, "quote-key-1", quoteBody);
    assertEquals(201, quoteReplay.statusCode());
    assertEquals(quoteCreated.body(), quoteReplay.body());
    HttpResponse<String> quoteConflict = postWithTenantAndIdempotency(
        "/api/v1/quotes", tenantId, "quote-key-1",
        quoteBody.replace("13.0", "13.01"));
    assertEquals(409, quoteConflict.statusCode());
    assertTrue(quoteConflict.body().contains("idempotency-key-reused"));
    assertEquals(200,
        getWithTenant("/api/v1/quotes/" + quoteId, tenantId, "platform-admin").statusCode());
    assertEquals(1, JsonParser.parseString(
        getWithTenant("/api/v1/quotes", tenantId, "platform-admin").body()).getAsJsonArray().size());
    assertEquals(404,
        getWithTenant("/api/v1/quotes/00000000-0000-0000-0000-000000000000",
            tenantId, "platform-admin").statusCode());

    HttpResponse<String> auditEvents = getWithTenant(
        "/api/v1/admin/audit-events", tenantId, "platform-admin");
    assertEquals(200, auditEvents.statusCode());
    JsonArray auditList = JsonParser.parseString(auditEvents.body()).getAsJsonArray();
    assertEquals(1, auditList.size());
    JsonObject audit = auditList.get(0).getAsJsonObject();
    assertEquals("fare_quote.create", audit.get("action").getAsString());
    assertEquals(quoteId, audit.get("targetId").getAsString());
    assertEquals("marketplace-http-it", audit.get("correlationId").getAsString());

    UUID tenantUuid = UUID.fromString(tenantId);
    List<OutboxEvent> leased = outboxPoller.lease(tenantUuid, 10, Duration.ofSeconds(30));
    assertEquals(1, leased.size());
    assertEquals("marketplace-http-it", leased.get(0).correlationId());
    outboxPoller.retry(tenantUuid, leased.get(0).id(), Instant.now().minusSeconds(1), "temporary");
    List<OutboxEvent> retried = outboxPoller.lease(tenantUuid, 10, Duration.ofSeconds(30));
    assertEquals(2, retried.get(0).attempts());
    outboxPoller.published(tenantUuid, retried.get(0).id());
    assertTrue(outboxPoller.lease(tenantUuid, 10, Duration.ofSeconds(30)).isEmpty());

    UUID incomingEvent = UUID.randomUUID();
    assertTrue(inbox.receive(tenantUuid, "marketplace-http-it", incomingEvent));
    assertFalse(inbox.receive(tenantUuid, "marketplace-http-it", incomingEvent));
    UUID auditId = UUID.fromString(audit.get("id").getAsString());
    assertThrows(DataAccessException.class,
        () -> jdbc.sql("UPDATE audit_events SET action = 'changed' WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantUuid).param("id", auditId).update());

    HttpResponse<String> route =
        postWithTenant(
            "/api/v1/routes/estimate",
            tenantId,
            "{\"origin\":{\"latitude\":12.9,\"longitude\":77.5},\"destination\":{\"latitude\":13.0,\"longitude\":77.6}}");
    assertEquals(200, route.statusCode());
    JsonObject estimate = JsonParser.parseString(route.body()).getAsJsonObject();
    assertEquals(2450.5, estimate.get("distanceMeters").getAsDouble());
    assertEquals(480.0, estimate.get("durationSeconds").getAsDouble());

    HttpResponse<String> onlineForDispatch = postWithTenant(
        "/api/v1/driver/shifts/" + shiftId + "/go-online", tenantId, "{\"version\":2}");
    assertEquals(200, onlineForDispatch.statusCode());
    String recordedAt = Instant.now().toString();
    String locationBody = "{\"shiftId\":\"" + shiftId
        + "\",\"latitude\":12.95,\"longitude\":77.6,\"recordedAt\":\""
        + recordedAt + "\",\"sequence\":1}";
    assertEquals(200, putWithTenant("/api/v1/driver/location", tenantId, locationBody).statusCode());
    assertEquals(409, putWithTenant("/api/v1/driver/location", tenantId, locationBody).statusCode());

    HttpResponse<String> rideCreated = postWithTenantAndIdempotency(
        "/api/v1/rides", tenantId, "ride-key-1", "{\"quoteId\":\"" + quoteId + "\"}");
    assertEquals(201, rideCreated.statusCode(), rideCreated.body());
    JsonObject ride = JsonParser.parseString(rideCreated.body()).getAsJsonObject();
    String rideId = ride.get("id").getAsString();
    assertEquals("REQUESTED", ride.get("status").getAsString());
    assertEquals(rideCreated.body(), postWithTenantAndIdempotency(
        "/api/v1/rides", tenantId, "ride-key-1", "{\"quoteId\":\"" + quoteId + "\"}").body());
    assertEquals(1, JsonParser.parseString(
        getWithTenant("/api/v1/rides", tenantId, "platform-admin").body()).getAsJsonArray().size());

    HttpResponse<String> offersCreated = postWithTenant(
        "/api/v1/dispatch/rides/" + rideId + "/start", tenantId, "{\"version\":0}");
    assertEquals(200, offersCreated.statusCode(), offersCreated.body());
    JsonArray offers = JsonParser.parseString(offersCreated.body()).getAsJsonArray();
    assertEquals(1, offers.size());
    String offerId = offers.get(0).getAsJsonObject().get("id").getAsString();
    assertEquals(1, JsonParser.parseString(
        getWithTenant("/api/v1/driver/offers", tenantId, "platform-admin").body()).getAsJsonArray().size());

    HttpRequest acceptRequest = tenantPostRequest(
        "/api/v1/driver/offers/" + offerId + "/accept", tenantId, "");
    CompletableFuture<HttpResponse<String>> firstAccept = httpClient.sendAsync(
        acceptRequest, HttpResponse.BodyHandlers.ofString());
    CompletableFuture<HttpResponse<String>> secondAccept = httpClient.sendAsync(
        acceptRequest, HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> firstResult = firstAccept.join();
    HttpResponse<String> secondResult = secondAccept.join();
    assertEquals(Set.of(200, 409), Set.of(firstResult.statusCode(), secondResult.statusCode()));
    HttpResponse<String> accepted = firstResult.statusCode() == 200 ? firstResult : secondResult;
    assertEquals("DRIVER_ASSIGNED",
        JsonParser.parseString(accepted.body()).getAsJsonObject().get("status").getAsString());

    JsonObject arriving = driverRideAction(tenantId, rideId, "arriving", 2);
    assertEquals("DRIVER_ARRIVING", arriving.get("status").getAsString());
    JsonObject arrived = driverRideAction(tenantId, rideId, "arrive", 3);
    assertEquals("DRIVER_ARRIVED", arrived.get("status").getAsString());
    JsonObject started = driverRideAction(tenantId, rideId, "start", 4);
    assertEquals("IN_PROGRESS", started.get("status").getAsString());
    JsonObject completed = driverRideAction(tenantId, rideId, "complete", 5);
    assertEquals("COMPLETED", completed.get("status").getAsString());
    assertEquals(409, postWithTenant(
        "/api/v1/driver/rides/" + rideId + "/complete", tenantId, "{\"version\":5}").statusCode());

    HttpResponse<String> pendingPaymentResponse = getWithTenant(
        "/api/v1/rides/" + rideId + "/payment", tenantId, "platform-admin");
    assertEquals(200, pendingPaymentResponse.statusCode(), pendingPaymentResponse.body());
    JsonObject pendingPayment = JsonParser.parseString(pendingPaymentResponse.body()).getAsJsonObject();
    String paymentId = pendingPayment.get("id").getAsString();
    assertEquals("CAPTURE_PENDING", pendingPayment.get("state").getAsString());
    assertEquals(809, pendingPayment.get("amountMinor").getAsLong());

    List<OutboxEvent> paymentEvents = outboxPoller.lease(tenantUuid, 100, Duration.ofSeconds(30));
    OutboxEvent captureRequest = paymentEvents.stream()
        .filter(event -> event.eventType().equals("payment.capture_requested"))
        .findFirst().orElseThrow();
    assertTrue(paymentWorker.process(captureRequest));
    paymentEvents.forEach(event -> outboxPoller.published(tenantUuid, event.id()));

    PaymentAccount paymentAccount = paymentRepository.findAccountForPayment(
        tenantUuid, UUID.fromString(paymentId)).orElseThrow();
    Instant captureTimestamp = Instant.now();
    String captureBody = "{\"eventId\":\"capture-event-1\",\"type\":\"CAPTURE_SUCCEEDED\","
        + "\"paymentId\":\"" + paymentId + "\",\"refundId\":null,"
        + "\"providerObjectId\":\"fake-pay-" + paymentId + "\",\"providerVersion\":1,"
        + "\"amountMinor\":809,\"currency\":\"USD\",\"failureCode\":null}";
    HttpResponse<String> captureCallback = providerCallback(
        paymentAccount.id(), captureTimestamp, captureBody);
    assertEquals(200, captureCallback.statusCode(), captureCallback.body());
    assertTrue(JsonParser.parseString(captureCallback.body()).getAsJsonObject().get("applied").getAsBoolean());
    assertEquals("CAPTURED", JsonParser.parseString(getWithTenant(
        "/api/v1/payments/" + paymentId, tenantId, "platform-admin").body())
        .getAsJsonObject().get("state").getAsString());
    assertEquals(688, JsonParser.parseString(getWithTenant(
        "/api/v1/driver/earnings", tenantId, "platform-admin").body())
        .getAsJsonArray().get(0).getAsJsonObject().get("availableMinor").getAsLong());

    HttpResponse<String> refundResponse = postWithTenant(
        "/api/v1/finance/payments/" + paymentId + "/refunds", tenantId,
        "{\"amountMinor\":200,\"reason\":\"service adjustment\"}");
    assertEquals(201, refundResponse.statusCode(), refundResponse.body());
    String refundId = JsonParser.parseString(refundResponse.body()).getAsJsonObject()
        .get("id").getAsString();
    List<OutboxEvent> refundEvents = outboxPoller.lease(tenantUuid, 100, Duration.ofSeconds(30));
    OutboxEvent refundRequest = refundEvents.stream()
        .filter(event -> event.eventType().equals("payment.refund_requested"))
        .findFirst().orElseThrow();
    assertTrue(paymentWorker.process(refundRequest));
    refundEvents.forEach(event -> outboxPoller.published(tenantUuid, event.id()));

    Instant refundTimestamp = Instant.now();
    String refundBody = "{\"eventId\":\"refund-event-1\",\"type\":\"REFUND_SUCCEEDED\","
        + "\"paymentId\":\"" + paymentId + "\",\"refundId\":\"" + refundId + "\","
        + "\"providerObjectId\":\"fake-refund-" + refundId + "\",\"providerVersion\":1,"
        + "\"amountMinor\":200,\"currency\":\"USD\",\"failureCode\":null}";
    assertEquals(200, providerCallback(paymentAccount.id(), refundTimestamp, refundBody).statusCode());
    JsonObject refunded = JsonParser.parseString(getWithTenant(
        "/api/v1/finance/refunds/" + refundId, tenantId, "platform-admin").body()).getAsJsonObject();
    assertEquals("SUCCEEDED", refunded.get("state").getAsString());
    assertEquals(518, JsonParser.parseString(getWithTenant(
        "/api/v1/driver/earnings", tenantId, "platform-admin").body())
        .getAsJsonArray().get(0).getAsJsonObject().get("availableMinor").getAsLong());
  }

  private JsonObject driverRideAction(String tenantId, String rideId, String action, long version)
      throws Exception {
    HttpResponse<String> response = postWithTenant(
        "/api/v1/driver/rides/" + rideId + "/" + action, tenantId,
        "{\"version\":" + version + "}");
    assertEquals(200, response.statusCode(), response.body());
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    return post(path, body, true);
  }

  private HttpResponse<String> postWithoutToken(String path, String body) throws Exception {
    return post(path, body, false);
  }

  private HttpResponse<String> postWithTenant(String path, String tenantId, String body)
      throws Exception {
    return httpClient.send(tenantPostRequest(path, tenantId, body),
        HttpResponse.BodyHandlers.ofString());
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
    HttpRequest request = HttpRequest.newBuilder(uri(path))
        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .header("Authorization", "Bearer platform-admin")
        .header("X-Tenant-ID", tenantId)
        .PUT(HttpRequest.BodyPublishers.ofString(body))
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String body, boolean authenticated) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    if (authenticated) {
      builder.header("Authorization", "Bearer platform-admin");
    }
    HttpRequest request =
        builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> providerCallback(
      UUID accountId, Instant timestamp, String body) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri(
            "/api/v1/payment-providers/fake/accounts/" + accountId + "/events"))
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
              .claim("scope", "platform.admin")
              .claim("email", "admin@example.com")
              .claim("name", "Platform Admin")
              .build();
    }
  }
}

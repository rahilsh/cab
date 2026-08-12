package in.rsh.cab.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
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

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
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

    HttpResponse<String> route =
        postWithTenant(
            "/api/v1/routes/estimate",
            tenantId,
            "{\"origin\":{\"latitude\":12.9,\"longitude\":77.5},\"destination\":{\"latitude\":13.0,\"longitude\":77.6}}");
    assertEquals(200, route.statusCode());
    JsonObject estimate = JsonParser.parseString(route.body()).getAsJsonObject();
    assertEquals(2450.5, estimate.get("distanceMeters").getAsDouble());
    assertEquals(480.0, estimate.get("durationSeconds").getAsDouble());
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    return post(path, body, true);
  }

  private HttpResponse<String> postWithoutToken(String path, String body) throws Exception {
    return post(path, body, false);
  }

  private HttpResponse<String> postWithTenant(String path, String tenantId, String body)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer platform-admin")
            .header("X-Tenant-ID", tenantId)
            .POST(HttpRequest.BodyPublishers.ofString(body))
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

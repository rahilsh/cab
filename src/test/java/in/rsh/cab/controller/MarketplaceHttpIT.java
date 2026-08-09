package in.rsh.cab.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    assertEquals(
        403,
        getWithTenant("/api/v1/current-tenant", tenantId, "unknown-user").statusCode());
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    return post(path, body, true);
  }

  private HttpResponse<String> postWithoutToken(String path, String body) throws Exception {
    return post(path, body, false);
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

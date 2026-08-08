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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    assertEquals(200, post("/cities", "{\"name\":\"BLR\",\"state\":\"KA\"}").statusCode());
    assertEquals(200, post("/cities", "{\"name\":\"MUM\",\"state\":\"MH\"}").statusCode());
    assertEquals(
        200,
        post("/cabs", "{\"cityId\":1,\"driverId\":1,\"model\":\"HECTOR\"}")
            .statusCode());

    HttpResponse<String> booking =
        post("/bookings", "{\"employeeId\":\"1\",\"fromCity\":1,\"toCity\":2}");
    assertEquals(200, booking.statusCode());
    JsonObject createdBooking = JsonParser.parseString(booking.body()).getAsJsonObject();
    assertEquals("1", createdBooking.get("bookedBy").getAsString());
    assertEquals(1, createdBooking.get("cabId").getAsInt());

    HttpResponse<String> bookings = get("/bookings");
    assertEquals(200, bookings.statusCode());
    JsonArray content =
        JsonParser.parseString(bookings.body()).getAsJsonObject().getAsJsonArray("content");
    assertTrue(content.isJsonArray());
    assertEquals(1, content.size());
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
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

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}

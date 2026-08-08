package in.rsh.cab.routing.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.routing.RouteEstimate;
import in.rsh.cab.routing.RouteProviderException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OsrmRouteEstimatorTest {

  private HttpServer server;
  private URI baseUri;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
    baseUri = URI.create("http://localhost:" + server.getAddress().getPort() + "/");
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void sendsOsrmCoordinatesInLongitudeLatitudeOrderAndMapsEstimate() {
    AtomicReference<String> requestUri = new AtomicReference<>();
    server.createContext(
        "/route/v1/driving/",
        exchange -> {
          requestUri.set(exchange.getRequestURI().toString());
          byte[] body =
              "{\"code\":\"Ok\",\"routes\":[{\"distance\":1234.5,\"duration\":321.0}]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    RouteEstimate estimate = estimator(baseUri).estimate(new GeoPoint(12.9, 77.5), new GeoPoint(13.0, 77.6));

    assertEquals(1234.5, estimate.distanceMeters());
    assertEquals(321.0, estimate.durationSeconds());
    assertTrue(requestUri.get().startsWith("/route/v1/driving/77.5,12.9;77.6,13.0?overview=false"));
  }

  @Test
  void mapsHttpAndMalformedResponsesToBadGatewayWithoutBodyLeak() {
    server.createContext(
        "/route/v1/driving/",
        exchange -> {
          byte[] body = "provider secret".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    RouteProviderException httpFailure =
        assertThrows(
            RouteProviderException.class,
            () -> estimator(baseUri).estimate(new GeoPoint(0, 0), new GeoPoint(1, 1)));
    assertEquals(RouteProviderException.Reason.BAD_RESPONSE, httpFailure.reason());
    assertTrue(!httpFailure.getMessage().contains("secret"));

    server.removeContext("/route/v1/driving/");
    server.createContext(
        "/route/v1/driving/",
        exchange -> {
          byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    RouteProviderException malformed =
        assertThrows(
            RouteProviderException.class,
            () -> estimator(baseUri).estimate(new GeoPoint(0, 0), new GeoPoint(1, 1)));
    assertEquals(RouteProviderException.Reason.BAD_RESPONSE, malformed.reason());
  }

  @Test
  void mapsConnectionFailureToUnavailable() {
    server.stop(0);
    RouteProviderException exception =
        assertThrows(
            RouteProviderException.class,
            () -> estimator(baseUri).estimate(new GeoPoint(0, 0), new GeoPoint(1, 1)));
    assertEquals(RouteProviderException.Reason.UNAVAILABLE, exception.reason());
  }

  private OsrmRouteEstimator estimator(URI uri) {
    return new OsrmRouteEstimator(
        uri,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
        new ObjectMapper());
  }
}

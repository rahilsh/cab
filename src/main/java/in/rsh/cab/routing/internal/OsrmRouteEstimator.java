package in.rsh.cab.routing.internal;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.routing.RouteEstimate;
import in.rsh.cab.routing.RouteEstimator;
import in.rsh.cab.routing.RouteProviderException;
import in.rsh.cab.routing.RouteProviderException.Reason;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OsrmRouteEstimator implements RouteEstimator {

  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

  private final URI baseUri;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public OsrmRouteEstimator(
      @Value("${routing.osrm.base-url:http://localhost:5000}") URI baseUri,
      ObjectMapper objectMapper) {
    this(
        baseUri,
        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
        objectMapper);
  }

  OsrmRouteEstimator(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper) {
    this.baseUri = baseUri;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public RouteEstimate estimate(GeoPoint origin, GeoPoint destination) {
    URI uri = routeUri(origin, destination);
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RouteProviderException(Reason.BAD_RESPONSE);
      }
      return parse(response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RouteProviderException(Reason.UNAVAILABLE);
    } catch (IOException exception) {
      throw new RouteProviderException(Reason.UNAVAILABLE);
    }
  }

  private URI routeUri(GeoPoint origin, GeoPoint destination) {
    String coordinates =
        origin.longitude()
            + ","
            + origin.latitude()
            + ";"
            + destination.longitude()
            + ","
            + destination.latitude();
    String encodedCoordinates = URLEncoder.encode(coordinates, StandardCharsets.UTF_8)
        .replace("%2C", ",")
        .replace("%3B", ";");
    String base = baseUri.toString().replaceAll("/+$", "");
    return URI.create(base + "/route/v1/driving/" + encodedCoordinates + "?overview=false");
  }

  private RouteEstimate parse(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode route = root.path("routes").path(0);
      if (!"Ok".equals(root.path("code").asText())
          || !route.has("distance")
          || !route.get("distance").isNumber()
          || !route.has("duration")
          || !route.get("duration").isNumber()) {
        throw new RouteProviderException(Reason.BAD_RESPONSE);
      }
      return new RouteEstimate(route.get("distance").asDouble(), route.get("duration").asDouble());
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new RouteProviderException(Reason.BAD_RESPONSE);
    }
  }
}

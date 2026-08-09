package in.rsh.cab.routing.internal.web;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.routing.RouteEstimate;
import in.rsh.cab.routing.RouteEstimator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

  private final RouteEstimator routeEstimator;

  public RouteController(RouteEstimator routeEstimator) {
    this.routeEstimator = routeEstimator;
  }

  @PostMapping("/estimate")
  public RouteEstimate estimate(@Valid @RequestBody RouteEstimateRequest request) {
    return routeEstimator.estimate(request.origin().toGeoPoint(), request.destination().toGeoPoint());
  }

  public record RouteEstimateRequest(@NotNull @Valid Point origin, @NotNull @Valid Point destination) {}

  public record Point(@NotNull Double latitude, @NotNull Double longitude) {
    GeoPoint toGeoPoint() {
      return new GeoPoint(latitude, longitude);
    }
  }
}

package in.rsh.cab.location.internal.web;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.location.DriverLocation;
import in.rsh.cab.location.DriverLocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/location")
public class DriverLocationController {

  private final DriverLocationService locations;

  public DriverLocationController(DriverLocationService locations) {
    this.locations = locations;
  }

  @PutMapping
  public DriverLocation update(@Valid @RequestBody UpdateLocationRequest request) {
    return locations.update(request.shiftId(),
        new GeoPoint(request.latitude(), request.longitude()), request.recordedAt(), request.sequence());
  }

  public record UpdateLocationRequest(
      @NotNull UUID shiftId,
      @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
      @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
      @NotNull Instant recordedAt,
      @PositiveOrZero long sequence) {}
}

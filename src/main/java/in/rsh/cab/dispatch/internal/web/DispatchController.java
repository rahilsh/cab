package in.rsh.cab.dispatch.internal.web;

import in.rsh.cab.dispatch.DispatchService;
import in.rsh.cab.dispatch.DriverOffer;
import in.rsh.cab.ride.Ride;
import in.rsh.cab.ride.RideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DispatchController {

  private final DispatchService dispatch;
  private final RideService rides;

  public DispatchController(DispatchService dispatch, RideService rides) {
    this.dispatch = dispatch;
    this.rides = rides;
  }

  @PostMapping("/api/v1/dispatch/rides/{rideId}/start")
  public List<DriverOffer> start(
      @PathVariable UUID rideId, @Valid @RequestBody VersionRequest request) {
    return dispatch.start(rideId, request.version());
  }

  @GetMapping("/api/v1/driver/offers")
  public List<DriverOffer> offers() {
    return dispatch.listOwnOffers();
  }

  @PostMapping("/api/v1/driver/offers/{offerId}/accept")
  public Ride accept(@PathVariable UUID offerId) {
    return dispatch.accept(offerId);
  }

  @PostMapping("/api/v1/driver/offers/{offerId}/reject")
  public ResponseEntity<Void> reject(@PathVariable UUID offerId) {
    dispatch.reject(offerId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/driver/rides/{rideId}/{action}")
  public Ride driverAction(
      @PathVariable UUID rideId,
      @PathVariable String action,
      @Valid @RequestBody ActionRequest request) {
    RideService.DriverAction driverAction =
        switch (action) {
          case "arriving" -> RideService.DriverAction.ARRIVING;
          case "arrive" -> RideService.DriverAction.ARRIVE;
          case "start" -> RideService.DriverAction.START;
          case "complete" -> RideService.DriverAction.COMPLETE;
          case "cancel" -> RideService.DriverAction.CANCEL;
          default -> throw new IllegalArgumentException("Unknown driver ride action");
        };
    return rides.driverAction(rideId, request.version(), driverAction, request.reason());
  }

  @PostMapping("/api/v1/dispatch/rides/{rideId}/cancel")
  public Ride adminCancel(@PathVariable UUID rideId, @Valid @RequestBody ReasonRequest request) {
    return rides.adminCancel(rideId, request.version(), request.reason());
  }

  public record VersionRequest(@PositiveOrZero long version) {}

  public record ActionRequest(@PositiveOrZero long version, String reason) {}

  public record ReasonRequest(
      @PositiveOrZero long version, @NotBlank @Size(max = 500) String reason) {}
}

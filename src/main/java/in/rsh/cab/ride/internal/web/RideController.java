package in.rsh.cab.ride.internal.web;

import in.rsh.cab.ride.Ride;
import in.rsh.cab.ride.RideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

  private final RideService rides;

  public RideController(RideService rides) {
    this.rides = rides;
  }

  @PostMapping
  public ResponseEntity<Ride> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateRideRequest request) {
    RideService.RideCreation result = rides.create(idempotencyKey, request.quoteId());
    return ResponseEntity.status(201).location(URI.create("/api/v1/rides/" + result.ride().id()))
        .body(result.ride());
  }

  @GetMapping
  public List<Ride> list() {
    return rides.listOwn();
  }

  @GetMapping("/{id}")
  public Ride get(@PathVariable UUID id) {
    return rides.getOwn(id);
  }

  @PostMapping("/{id}/cancel")
  public Ride cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    return rides.cancelOwn(id, request.version(), request.reason());
  }

  public record CreateRideRequest(@NotNull UUID quoteId) {}

  public record ReasonRequest(
      @PositiveOrZero long version, @NotBlank @Size(max = 500) String reason) {}
}

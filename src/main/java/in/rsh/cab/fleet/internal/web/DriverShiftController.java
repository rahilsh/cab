package in.rsh.cab.fleet.internal.web;

import in.rsh.cab.fleet.DriverShift;
import in.rsh.cab.fleet.FleetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/shifts")
public class DriverShiftController {

  private final FleetService fleet;

  public DriverShiftController(FleetService fleet) {
    this.fleet = fleet;
  }

  @PostMapping
  public ResponseEntity<DriverShift> create(@Valid @RequestBody CreateShiftRequest request) {
    DriverShift shift = fleet.createShift(request.driverId(), request.vehicleId());
    return ResponseEntity.created(URI.create("/api/v1/driver/shifts")).body(shift);
  }

  @GetMapping
  public List<DriverShift> listOwn() {
    return fleet.listOwnShifts();
  }

  @PostMapping("/{id}/go-online")
  public DriverShift goOnline(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
    return fleet.goOnline(id, request.version());
  }

  @PostMapping("/{id}/go-offline")
  public DriverShift goOffline(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
    return fleet.goOffline(id, request.version());
  }

  @PostMapping("/{id}/close")
  public DriverShift close(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
    return fleet.closeShift(id, request.version());
  }

  public record CreateShiftRequest(@NotNull UUID driverId, @NotNull UUID vehicleId) {}

  public record VersionRequest(@PositiveOrZero long version) {}
}

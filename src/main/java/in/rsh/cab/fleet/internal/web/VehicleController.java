package in.rsh.cab.fleet.internal.web;

import in.rsh.cab.fleet.FleetService;
import in.rsh.cab.fleet.Vehicle;
import in.rsh.cab.fleet.VehicleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

  private final FleetService fleet;

  public VehicleController(FleetService fleet) {
    this.fleet = fleet;
  }

  @PostMapping
  public ResponseEntity<Vehicle> create(@Valid @RequestBody CreateVehicleRequest request) {
    Vehicle vehicle = fleet.createVehicle(request.registration(), request.serviceClass(), request.capacity());
    return ResponseEntity.created(URI.create("/api/v1/vehicles/" + vehicle.id())).body(vehicle);
  }

  @GetMapping
  public List<Vehicle> list() {
    return fleet.listVehicles();
  }

  @PutMapping("/{id}")
  public Vehicle update(@PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request) {
    return fleet.updateVehicle(
        id, request.registration(), request.serviceClass(), request.capacity(), request.status(),
        request.version());
  }

  public record CreateVehicleRequest(
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]+$") @Size(max = 32) String registration,
      @NotBlank @Size(max = 32) String serviceClass,
      @Min(1) @Max(20) int capacity) {}

  public record UpdateVehicleRequest(
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]+$") @Size(max = 32) String registration,
      @NotBlank @Size(max = 32) String serviceClass,
      @Min(1) @Max(20) int capacity,
      @NotNull VehicleStatus status,
      @PositiveOrZero long version) {}
}

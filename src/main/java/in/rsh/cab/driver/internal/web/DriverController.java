package in.rsh.cab.driver.internal.web;

import in.rsh.cab.driver.DriverProfile;
import in.rsh.cab.driver.DriverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/v1/drivers")
public class DriverController {

  private final DriverService drivers;

  public DriverController(DriverService drivers) {
    this.drivers = drivers;
  }

  @PostMapping
  public ResponseEntity<DriverProfile> create(@Valid @RequestBody CreateDriverRequest request) {
    DriverProfile driver =
        drivers.create(request.accountId(), request.legalName(), request.phoneNumber());
    return ResponseEntity.created(URI.create("/api/v1/drivers")).body(driver);
  }

  @GetMapping
  public List<DriverProfile> list() {
    return drivers.list();
  }

  @PostMapping("/{id}/approve")
  public DriverProfile approve(@PathVariable UUID id) {
    return drivers.approve(id);
  }

  @PostMapping("/{id}/suspend")
  public DriverProfile suspend(@PathVariable UUID id) {
    return drivers.suspend(id);
  }

  @GetMapping("/me")
  public DriverProfile getOwn() {
    return drivers.getOwn();
  }

  @PutMapping("/me")
  public DriverProfile updateOwn(@Valid @RequestBody UpdateDriverRequest request) {
    return drivers.updateOwn(request.legalName(), request.phoneNumber());
  }

  public record CreateDriverRequest(
      @NotNull UUID accountId,
      @NotBlank @Size(max = 120) String legalName,
      @Pattern(regexp = "^\\+?[0-9 -]{7,32}$") String phoneNumber) {}

  public record UpdateDriverRequest(
      @NotBlank @Size(max = 120) String legalName,
      @Pattern(regexp = "^\\+?[0-9 -]{7,32}$") String phoneNumber) {}
}

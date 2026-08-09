package in.rsh.cab.geography.internal.web;

import in.rsh.cab.geography.ServiceArea;
import in.rsh.cab.geography.ServiceAreaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/service-areas")
public class ServiceAreaController {

  private final ServiceAreaService serviceAreaService;

  public ServiceAreaController(ServiceAreaService serviceAreaService) {
    this.serviceAreaService = serviceAreaService;
  }

  @PostMapping
  public ResponseEntity<ServiceArea> create(@Valid @RequestBody CreateServiceAreaRequest request) {
    ServiceArea serviceArea =
        serviceAreaService.create(
            request.slug(), request.name(), request.timezone(), request.boundary());
    return ResponseEntity.created(URI.create("/api/v1/service-areas/" + serviceArea.id()))
        .body(serviceArea);
  }

  @GetMapping
  public List<ServiceArea> list() {
    return serviceAreaService.list();
  }

  public record CreateServiceAreaRequest(
      @NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 63) String slug,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 64) String timezone,
      @NotNull JsonNode boundary) {}
}

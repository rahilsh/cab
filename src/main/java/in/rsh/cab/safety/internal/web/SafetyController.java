package in.rsh.cab.safety.internal.web;

import in.rsh.cab.safety.SafetyIncident;
import in.rsh.cab.safety.SafetyService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/safety/incidents")
public class SafetyController {

  private final SafetyService safety;

  public SafetyController(SafetyService safety) {
    this.safety = safety;
  }

  @PostMapping
  public ResponseEntity<SafetyIncident> report(@Valid @RequestBody ReportRequest request) {
    SafetyIncident incident = safety.report(request.rideId(), request.category(), request.description());
    return ResponseEntity.created(URI.create("/api/v1/safety/incidents/" + incident.id()))
        .body(incident);
  }

  @GetMapping
  public List<SafetyIncident> list() {
    return safety.listRestricted();
  }

  @GetMapping("/{id}")
  public SafetyIncident get(@PathVariable UUID id) {
    return safety.get(id);
  }

  @PostMapping("/{id}/evidence")
  public ResponseEntity<SafetyIncident.Evidence> evidence(
      @PathVariable UUID id, @Valid @RequestBody EvidenceRequest request) {
    SafetyIncident.Evidence evidence = safety.addEvidence(id, request.version(), request.objectKey(),
        request.mediaType(), request.sizeBytes(), request.checksumSha256());
    return ResponseEntity.created(URI.create("/api/v1/safety/incidents/" + id)).body(evidence);
  }

  @PostMapping("/{id}/actions")
  public SafetyIncident action(@PathVariable UUID id, @Valid @RequestBody ActionRequest request) {
    return safety.action(id, request.action(), request.expectedState(), request.state(),
        request.severity(), request.version(), request.note());
  }

  public record ReportRequest(
      @NotNull UUID rideId,
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$") String category,
      @NotBlank @Size(max = 2000) String description) {}

  public record EvidenceRequest(
      @NotNull @PositiveOrZero Long version,
      @NotBlank @Size(max = 512) String objectKey,
      @NotBlank @Size(max = 120) String mediaType,
      @PositiveOrZero Long sizeBytes,
      @Pattern(regexp = "^[0-9a-f]{64}$") String checksumSha256) {}

  public record ActionRequest(
      @NotBlank @Size(max = 64) String action,
      @NotBlank @Pattern(regexp = "REPORTED|TRIAGED|INVESTIGATING|RESOLVED|CLOSED")
          String expectedState,
      @NotBlank @Pattern(regexp = "REPORTED|TRIAGED|INVESTIGATING|RESOLVED|CLOSED") String state,
      @NotBlank @Pattern(regexp = "UNASSESSED|LOW|MEDIUM|HIGH|CRITICAL") String severity,
      @PositiveOrZero long version,
      @Size(max = 500) String note) {}
}

package in.rsh.cab.support.internal.web;

import in.rsh.cab.support.SupportCase;
import in.rsh.cab.support.SupportService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/cases")
public class SupportController {

  private final SupportService support;

  public SupportController(SupportService support) {
    this.support = support;
  }

  @PostMapping
  public ResponseEntity<SupportCase> create(@Valid @RequestBody CreateCaseRequest request) {
    SupportCase supportCase = support.create(request.rideId(), request.subject(), request.message());
    return ResponseEntity.created(URI.create("/api/v1/support/cases/" + supportCase.id()))
        .body(supportCase);
  }

  @GetMapping
  public List<SupportCase> list() {
    return support.list();
  }

  @PostMapping("/{id}/messages")
  public SupportCase message(@PathVariable UUID id, @Valid @RequestBody MessageRequest request) {
    return support.addMessage(id, request.body(), request.internal());
  }

  @PostMapping("/{id}/state")
  public SupportCase state(@PathVariable UUID id, @Valid @RequestBody StateRequest request) {
    return support.changeState(id, request.state(), request.reason());
  }

  @PostMapping("/{id}/assignments")
  public ResponseEntity<Void> assign(
      @PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
    support.assign(id, request.assigneeAccountId());
    return ResponseEntity.noContent().build();
  }

  public record CreateCaseRequest(
      UUID rideId,
      @NotBlank @Size(max = 160) String subject,
      @NotBlank @Size(max = 4000) String message) {}

  public record MessageRequest(
      @NotBlank @Size(max = 4000) String body, boolean internal) {}

  public record StateRequest(
      @NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|WAITING|RESOLVED|CLOSED") String state,
      @Size(max = 500) String reason) {}

  public record AssignmentRequest(@NotNull UUID assigneeAccountId) {}
}

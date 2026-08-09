package in.rsh.cab.tenancy.internal.web;

import in.rsh.cab.tenancy.TenantService;
import in.rsh.cab.tenancy.TenantSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

  private final TenantService tenantService;

  public TenantController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<TenantSummary> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTenantRequest request) {
    TenantSummary tenant =
        tenantService.create(
            jwt.getIssuer().toString(),
            jwt.getSubject(),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name"),
            request.slug(),
            request.displayName(),
            request.defaultCurrency(),
            request.timezone());
    return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.id())).body(tenant);
  }

  @GetMapping
  public List<TenantSummary> list(@AuthenticationPrincipal Jwt jwt) {
    return tenantService.findForIdentity(jwt.getIssuer().toString(), jwt.getSubject());
  }

  public record CreateTenantRequest(
      @NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 63) String slug,
      @NotBlank @Size(max = 120) String displayName,
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String defaultCurrency,
      @NotBlank @Size(max = 64) String timezone) {}
}

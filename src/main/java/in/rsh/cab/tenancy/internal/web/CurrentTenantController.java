package in.rsh.cab.tenancy.internal.web;

import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import in.rsh.cab.tenancy.TenantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/current-tenant")
public class CurrentTenantController {

  private final TenantService tenantService;

  public CurrentTenantController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @GetMapping
  public TenantContext current() {
    return TenantContext.require();
  }

  @PostMapping("/roles/{role}")
  public ResponseEntity<Void> grantSelfServiceRole(@PathVariable TenantRole role) {
    tenantService.grantSelfServiceRole(role);
    return ResponseEntity.noContent().build();
  }
}

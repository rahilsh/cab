package in.rsh.cab.tenancy.internal.web;

import in.rsh.cab.tenancy.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/current-tenant")
public class CurrentTenantController {

  @GetMapping
  public TenantContext current() {
    return TenantContext.require();
  }
}

package in.rsh.cab.audit.internal.web;

import in.rsh.cab.audit.AuditEvent;
import in.rsh.cab.audit.AuditService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AuditController {

  private final AuditService audit;

  public AuditController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping
  public List<AuditEvent> list(@RequestParam(defaultValue = "100") int limit) {
    return audit.list(limit);
  }
}

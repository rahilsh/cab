package in.rsh.cab.dispatch;

import in.rsh.cab.location.DriverLocationReconciler;
import in.rsh.cab.tenancy.TenantExecution;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dispatch.maintenance.enabled", matchIfMissing = true)
public class DispatchMaintenance {

  private static final Logger log = LoggerFactory.getLogger(DispatchMaintenance.class);
  private final TenantRepository tenants;
  private final TenantExecution tenantExecution;
  private final DispatchService dispatch;
  private final DriverLocationReconciler locations;
  private final int batchSize;

  public DispatchMaintenance(
      TenantRepository tenants, TenantExecution tenantExecution, DispatchService dispatch,
      DriverLocationReconciler locations,
      @Value("${dispatch.maintenance.batch-size:100}") int batchSize) {
    this.tenants = tenants;
    this.tenantExecution = tenantExecution;
    this.dispatch = dispatch;
    this.locations = locations;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${dispatch.maintenance.fixed-delay:10000}")
  public void maintain() {
    for (UUID tenantId : tenants.findActiveIds()) {
      try {
        tenantExecution.inTransaction(tenantId, () -> {
          dispatch.sweepExpired(tenantId, batchSize);
          locations.reconcile(tenantId, batchSize);
        });
      } catch (RuntimeException exception) {
        log.warn("Dispatch maintenance failed tenant={}", tenantId, exception);
      }
    }
  }
}

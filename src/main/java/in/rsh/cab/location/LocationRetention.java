package in.rsh.cab.location;

import in.rsh.cab.location.internal.persistence.LocationCheckpointRepository;
import in.rsh.cab.tenancy.TenantExecution;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "retention.location.enabled", matchIfMissing = true)
public class LocationRetention {

  private static final Logger log = LoggerFactory.getLogger(LocationRetention.class);
  private final TenantRepository tenants;
  private final TenantExecution tenantExecution;
  private final LocationCheckpointRepository checkpoints;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public LocationRetention(
      TenantRepository tenants,
      TenantExecution tenantExecution,
      LocationCheckpointRepository checkpoints,
      Clock clock,
      @Value("${retention.location.duration:P30D}") Duration retention,
      @Value("${retention.location.batch-size:1000}") int batchSize) {
    if (retention.isNegative() || retention.isZero()) {
      throw new IllegalArgumentException("Location retention duration must be positive");
    }
    this.tenants = tenants;
    this.tenantExecution = tenantExecution;
    this.checkpoints = checkpoints;
    this.clock = clock;
    this.retention = retention;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${retention.location.fixed-delay:3600000}")
  public void purge() {
    for (UUID tenantId : tenants.findActiveIds()) {
      try {
        tenantExecution.inTransaction(tenantId,
            () -> checkpoints.deleteCreatedBefore(tenantId, clock.instant().minus(retention), batchSize));
      } catch (RuntimeException exception) {
        log.warn("Location retention purge failed tenant={}", tenantId, exception);
      }
    }
  }
}

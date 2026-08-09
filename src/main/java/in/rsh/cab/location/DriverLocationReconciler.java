package in.rsh.cab.location;

import in.rsh.cab.location.internal.persistence.LocationCheckpointRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DriverLocationReconciler {

  private static final Logger log = LoggerFactory.getLogger(DriverLocationReconciler.class);
  private final LocationCheckpointRepository checkpoints;
  private final LiveLocationStore locations;
  private final Clock clock;
  private final Duration maxAge;

  public DriverLocationReconciler(
      LocationCheckpointRepository checkpoints, LiveLocationStore locations, Clock clock,
      @Value("${dispatch.location-max-age:PT2M}") Duration maxAge) {
    this.checkpoints = checkpoints;
    this.locations = locations;
    this.clock = clock;
    this.maxAge = maxAge;
  }

  public void reconcile(UUID tenantId, int limit) {
    var now = clock.instant();
    for (DriverLocation location : checkpoints.findLatestEligible(
        tenantId, now.minus(maxAge), LocalDate.now(clock), limit)) {
      try {
        locations.update(tenantId, location);
      } catch (RuntimeException exception) {
        log.warn("Live location reconciliation failed tenant={} shift={}", tenantId,
            location.shiftId(), exception);
      }
    }
  }
}

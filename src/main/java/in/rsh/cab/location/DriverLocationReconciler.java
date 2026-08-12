package in.rsh.cab.location;

import in.rsh.cab.location.internal.persistence.LocationCheckpointRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
  private final ConcurrentMap<UUID, UUID> cursors = new ConcurrentHashMap<>();

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
    UUID cursor = cursors.get(tenantId);
    var page = checkpoints.findLatestEligibleAfter(
        tenantId, now.minus(maxAge), LocalDate.now(clock), cursor, limit);
    if (page.isEmpty() && cursor != null) {
      cursors.remove(tenantId, cursor);
      page = checkpoints.findLatestEligibleAfter(
          tenantId, now.minus(maxAge), LocalDate.now(clock), null, limit);
    }
    for (DriverLocation location : page) {
      try {
        if (!locations.isCurrent(tenantId, location)) {
          locations.update(tenantId, location);
        }
      } catch (RuntimeException exception) {
        log.warn("Live location reconciliation failed tenant={} shift={}", tenantId,
            location.shiftId(), exception);
      }
    }
    if (!page.isEmpty()) {
      cursors.put(tenantId, page.getLast().shiftId());
    }
  }
}

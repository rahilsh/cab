package in.rsh.cab.location;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.location.internal.persistence.LocationCheckpointRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DriverLocationService {

  private static final Logger log = LoggerFactory.getLogger(DriverLocationService.class);
  private final FleetRepository fleet;
  private final LiveLocationStore locations;
  private final LocationCheckpointRepository checkpoints;
  private final Clock clock;
  private final Duration maxAge;
  private final Duration futureTolerance;

  public DriverLocationService(
      FleetRepository fleet, LiveLocationStore locations, LocationCheckpointRepository checkpoints,
      Clock clock,
      @Value("${dispatch.location-max-age:PT2M}") Duration maxAge,
      @Value("${dispatch.location-future-tolerance:PT30S}") Duration futureTolerance) {
    this.fleet = fleet;
    this.locations = locations;
    this.checkpoints = checkpoints;
    this.clock = clock;
    this.maxAge = maxAge;
    this.futureTolerance = futureTolerance;
  }

  @Transactional
  public DriverLocation update(UUID shiftId, GeoPoint point, Instant recordedAt, long sequence) {
    TenantContext context = requireDriver();
    Instant now = clock.instant();
    if (sequence < 0) {
      throw new InvalidRequestException("Location sequence must be non-negative");
    }
    if (recordedAt.isBefore(now.minus(maxAge)) || recordedAt.isAfter(now.plus(futureTolerance))) {
      throw new InvalidRequestException("Location timestamp is outside the accepted freshness window");
    }
    var shift = fleet.findShift(context.tenantId(), shiftId, context.accountId())
        .orElseThrow(() -> new NotFoundException("Driver shift not found"));
    if (shift.status() != ShiftStatus.AVAILABLE) {
      throw new ConflictException("Driver shift must be available to update location");
    }
    DriverLocation location = new DriverLocation(shiftId, point, recordedAt, sequence);
    try {
      if (!checkpoints.insertIfNewer(context.tenantId(), location, now)) {
        throw new ConflictException("Location update is stale");
      }
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Location update is stale");
    }
    updateIndexAfterCommit(context.tenantId(), location);
    return location;
  }

  private void updateIndexAfterCommit(UUID tenantId, DriverLocation location) {
    Runnable update = () -> {
      try {
        locations.update(tenantId, location);
      } catch (RuntimeException exception) {
        log.warn("Live location index update failed; reconciliation will retry tenant={} shift={}",
            tenantId, location.shiftId(), exception);
      }
    };
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      update.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        update.run();
      }
    });
  }

  private TenantContext requireDriver() {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.DRIVER)) {
      throw new TenantAccessDeniedException("DRIVER role is required");
    }
    return context;
  }
}

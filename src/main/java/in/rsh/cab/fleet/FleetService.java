package in.rsh.cab.fleet;

import in.rsh.cab.driver.DriverStatus;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FleetService {

  private final FleetRepository fleet;
  private final Clock clock;

  public FleetService(FleetRepository fleet, Clock clock) {
    this.fleet = fleet;
    this.clock = clock;
  }

  @Transactional
  public Vehicle createVehicle(String registration, String serviceClass, int capacity) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    Instant now = clock.instant();
    Vehicle vehicle =
        new Vehicle(
            UUID.randomUUID(), normalize(registration), serviceClass, capacity,
            VehicleStatus.ACTIVE, 0, now, now);
    try {
      fleet.insertVehicle(context.tenantId(), vehicle);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Vehicle registration is already in use");
    }
    return vehicle;
  }

  @Transactional(readOnly = true)
  public List<Vehicle> listVehicles() {
    return fleet.findVehicles(require(TenantRole.TENANT_ADMIN).tenantId());
  }

  @Transactional
  public Vehicle updateVehicle(
      UUID id, String registration, String serviceClass, int capacity, VehicleStatus status,
      long version) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    Vehicle current = fleet.findVehicle(context.tenantId(), id)
        .orElseThrow(() -> new NotFoundException("Vehicle not found"));
    if (current.version() != version) {
      throw new ConflictException("Vehicle version is stale");
    }
    Vehicle updated = current.update(normalize(registration), serviceClass, capacity, status, clock.instant());
    try {
      if (!fleet.updateVehicle(context.tenantId(), updated, version)) {
        throw new ConflictException("Vehicle changed concurrently");
      }
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Vehicle registration is already in use");
    }
    return updated;
  }

  @Transactional
  public DriverShift createShift(UUID driverId, UUID vehicleId) {
    TenantContext context = require(TenantRole.DRIVER);
    DriverStatus driverStatus =
        fleet.findDriverStatus(context.tenantId(), driverId, context.accountId())
            .orElseThrow(() -> new NotFoundException("Driver profile not found"));
    if (driverStatus != DriverStatus.APPROVED) {
      throw new ConflictException("Driver must be approved to start a shift");
    }
    Vehicle vehicle = fleet.findVehicle(context.tenantId(), vehicleId)
        .orElseThrow(() -> new NotFoundException("Vehicle not found"));
    if (vehicle.status() != VehicleStatus.ACTIVE) {
      throw new ConflictException("Vehicle must be active to start a shift");
    }
    Instant now = clock.instant();
    DriverShift shift =
        new DriverShift(
            UUID.randomUUID(), driverId, vehicleId, ShiftStatus.OFFLINE, 0, now, now, null, null);
    try {
      fleet.insertShift(context.tenantId(), shift);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Driver or vehicle already has an open shift");
    }
    return shift;
  }

  @Transactional(readOnly = true)
  public List<DriverShift> listOwnShifts() {
    TenantContext context = require(TenantRole.DRIVER);
    return fleet.findShifts(context.tenantId(), context.accountId());
  }

  @Transactional
  public DriverShift goOnline(UUID shiftId, long version) {
    return transition(shiftId, version, Transition.ONLINE);
  }

  @Transactional
  public DriverShift goOffline(UUID shiftId, long version) {
    return transition(shiftId, version, Transition.OFFLINE);
  }

  @Transactional
  public DriverShift closeShift(UUID shiftId, long version) {
    return transition(shiftId, version, Transition.CLOSE);
  }

  private DriverShift transition(UUID shiftId, long version, Transition transition) {
    TenantContext context = require(TenantRole.DRIVER);
    DriverShift current = fleet.findShift(context.tenantId(), shiftId, context.accountId())
        .orElseThrow(() -> new NotFoundException("Driver shift not found"));
    if (current.version() != version) {
      throw new ConflictException("Driver shift version is stale");
    }
    DriverShift updated = switch (transition) {
      case ONLINE -> current.goOnline(clock.instant());
      case OFFLINE -> current.goOffline(clock.instant());
      case CLOSE -> current.close(clock.instant());
    };
    if (!fleet.updateShift(context.tenantId(), updated, version)) {
      throw new ConflictException("Driver shift changed concurrently");
    }
    return updated;
  }

  private TenantContext require(TenantRole role) {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(role)) {
      throw new TenantAccessDeniedException(role + " role is required");
    }
    return context;
  }

  private String normalize(String registration) {
    return registration.replaceAll("\\s+", "").toUpperCase();
  }

  private enum Transition { ONLINE, OFFLINE, CLOSE }
}

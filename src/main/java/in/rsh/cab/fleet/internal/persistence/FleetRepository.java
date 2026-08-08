package in.rsh.cab.fleet.internal.persistence;

import in.rsh.cab.driver.DriverStatus;
import in.rsh.cab.fleet.DriverShift;
import in.rsh.cab.fleet.ShiftStatus;
import in.rsh.cab.fleet.Vehicle;
import in.rsh.cab.fleet.VehicleStatus;
import in.rsh.cab.fleet.SupplyCandidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FleetRepository {

  void insertVehicle(UUID tenantId, Vehicle vehicle);

  Optional<Vehicle> findVehicle(UUID tenantId, UUID id);

  List<Vehicle> findVehicles(UUID tenantId);

  boolean updateVehicle(UUID tenantId, Vehicle vehicle, long expectedVersion);

  Optional<DriverStatus> findDriverStatus(UUID tenantId, UUID driverId, UUID accountId);

  void insertShift(UUID tenantId, DriverShift shift);

  Optional<DriverShift> findShift(UUID tenantId, UUID shiftId, UUID accountId);

  Optional<DriverShift> findShift(UUID tenantId, UUID shiftId);

  List<DriverShift> findShifts(UUID tenantId, UUID accountId);

  boolean updateShift(UUID tenantId, DriverShift shift, long expectedVersion);

  boolean transitionShift(
      UUID tenantId, UUID shiftId, ShiftStatus expected, ShiftStatus next, java.time.Instant now);

  List<SupplyCandidate> findAvailableCandidates(
      UUID tenantId, List<UUID> shiftIds, String serviceClass);
}

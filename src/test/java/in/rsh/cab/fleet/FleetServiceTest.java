package in.rsh.cab.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.driver.DriverStatus;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.fleet.internal.persistence.FleetRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FleetServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final FleetRepository repository = mock(FleetRepository.class);
  private FleetService service;

  @BeforeEach
  void setUp() {
    service = new FleetService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    admin();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void adminCreatesListsAndVersionUpdatesVehicle() {
    Vehicle created = service.createVehicle("ab 12 cd", "STANDARD", 4);
    assertEquals("AB12CD", created.registration());
    verify(repository).insertVehicle(TENANT_ID, created);
    when(repository.findVehicles(TENANT_ID)).thenReturn(List.of(created));
    assertEquals(List.of(created), service.listVehicles());

    when(repository.findVehicle(TENANT_ID, created.id())).thenReturn(Optional.of(created));
    when(repository.updateVehicle(any(), any(), any(Long.class))).thenReturn(true);
    Vehicle updated = service.updateVehicle(
        created.id(), "xy 99", "XL", 6, VehicleStatus.INACTIVE, 0);
    assertEquals(1, updated.version());
    assertEquals(VehicleStatus.INACTIVE, updated.status());
  }

  @Test
  void vehicleValidatesCapacityRoleDuplicatesAndOptimisticConflicts() {
    assertThrows(InvalidRequestException.class,
        () -> service.createVehicle("A", "STANDARD", 0));
    doThrow(new DataIntegrityViolationException("constraint"))
        .when(repository).insertVehicle(any(), any());
    assertThrows(ConflictException.class,
        () -> service.createVehicle("A", "STANDARD", 4));

    Vehicle vehicle = vehicle(VehicleStatus.ACTIVE, 2);
    when(repository.findVehicle(TENANT_ID, vehicle.id())).thenReturn(Optional.of(vehicle));
    assertThrows(ConflictException.class,
        () -> service.updateVehicle(vehicle.id(), "A", "STANDARD", 4, VehicleStatus.ACTIVE, 1));
    when(repository.updateVehicle(any(), any(), any(Long.class))).thenReturn(false);
    assertThrows(ConflictException.class,
        () -> service.updateVehicle(vehicle.id(), "A", "STANDARD", 4, VehicleStatus.ACTIVE, 2));
    assertThrows(NotFoundException.class,
        () -> service.updateVehicle(UUID.randomUUID(), "A", "STANDARD", 4, VehicleStatus.ACTIVE, 0));

    driver();
    assertThrows(TenantAccessDeniedException.class, service::listVehicles);
  }

  @Test
  void approvedDriverCreatesAndTransitionsOwnShift() {
    driver();
    UUID driverId = UUID.randomUUID();
    Vehicle vehicle = vehicle(VehicleStatus.ACTIVE, 0);
    when(repository.findDriverStatus(TENANT_ID, driverId, ACCOUNT_ID))
        .thenReturn(Optional.of(DriverStatus.APPROVED));
    when(repository.findVehicle(TENANT_ID, vehicle.id())).thenReturn(Optional.of(vehicle));
    DriverShift shift = service.createShift(driverId, vehicle.id());
    assertEquals(ShiftStatus.OFFLINE, shift.status());
    verify(repository).insertShift(TENANT_ID, shift);

    when(repository.findShift(TENANT_ID, shift.id(), ACCOUNT_ID)).thenReturn(Optional.of(shift));
    when(repository.updateShift(any(), any(), any(Long.class))).thenReturn(true);
    DriverShift online = service.goOnline(shift.id(), 0);
    assertEquals(ShiftStatus.AVAILABLE, online.status());
    assertNotNull(online.availableAt());

    when(repository.findShift(TENANT_ID, shift.id(), ACCOUNT_ID)).thenReturn(Optional.of(online));
    DriverShift offline = service.goOffline(shift.id(), 1);
    assertEquals(ShiftStatus.OFFLINE, offline.status());
    when(repository.findShift(TENANT_ID, shift.id(), ACCOUNT_ID)).thenReturn(Optional.of(offline));
    DriverShift closed = service.closeShift(shift.id(), 2);
    assertEquals(ShiftStatus.CLOSED, closed.status());
    assertNotNull(closed.closedAt());
  }

  @Test
  void shiftRejectsIneligibleResourcesDuplicatesAndStaleTransitions() {
    driver();
    UUID driverId = UUID.randomUUID();
    UUID vehicleId = UUID.randomUUID();
    when(repository.findDriverStatus(TENANT_ID, driverId, ACCOUNT_ID))
        .thenReturn(Optional.of(DriverStatus.PENDING));
    assertThrows(ConflictException.class, () -> service.createShift(driverId, vehicleId));
    when(repository.findDriverStatus(TENANT_ID, driverId, ACCOUNT_ID))
        .thenReturn(Optional.of(DriverStatus.APPROVED));
    when(repository.findVehicle(TENANT_ID, vehicleId))
        .thenReturn(Optional.of(vehicle(VehicleStatus.INACTIVE, 0)));
    assertThrows(ConflictException.class, () -> service.createShift(driverId, vehicleId));
    when(repository.findVehicle(TENANT_ID, vehicleId)).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> service.createShift(driverId, vehicleId));
    when(repository.findDriverStatus(TENANT_ID, driverId, ACCOUNT_ID)).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> service.createShift(driverId, vehicleId));

    DriverShift shift = shift(ShiftStatus.OFFLINE, 1);
    when(repository.findShift(TENANT_ID, shift.id(), ACCOUNT_ID)).thenReturn(Optional.of(shift));
    assertThrows(ConflictException.class, () -> service.goOnline(shift.id(), 0));
    assertThrows(ConflictException.class, () -> service.goOffline(shift.id(), 1));
    when(repository.updateShift(any(), any(), any(Long.class))).thenReturn(false);
    assertThrows(ConflictException.class, () -> service.goOnline(shift.id(), 1));
    assertThrows(NotFoundException.class, () -> service.goOnline(UUID.randomUUID(), 0));
  }

  @Test
  void listsOnlyOwnShiftsAndMapsOpenShiftConstraint() {
    driver();
    DriverShift shift = shift(ShiftStatus.OFFLINE, 0);
    when(repository.findShifts(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of(shift));
    assertEquals(List.of(shift), service.listOwnShifts());

    when(repository.findDriverStatus(TENANT_ID, shift.driverId(), ACCOUNT_ID))
        .thenReturn(Optional.of(DriverStatus.APPROVED));
    when(repository.findVehicle(TENANT_ID, shift.vehicleId()))
        .thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE, 0)));
    doThrow(new DataIntegrityViolationException("constraint"))
        .when(repository).insertShift(any(), any());
    assertThrows(ConflictException.class,
        () -> service.createShift(shift.driverId(), shift.vehicleId()));
  }

  private Vehicle vehicle(VehicleStatus status, long version) {
    return new Vehicle(UUID.randomUUID(), "AB12", "STANDARD", 4, status, version, NOW, NOW);
  }

  private DriverShift shift(ShiftStatus status, long version) {
    return new DriverShift(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status, version, NOW, NOW, null, null);
  }

  private void admin() {
    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.TENANT_ADMIN)));
  }

  private void driver() {
    TenantContext.set(new TenantContext(
        TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), Set.of(TenantRole.DRIVER)));
  }
}

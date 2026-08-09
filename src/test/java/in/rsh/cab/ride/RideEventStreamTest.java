package in.rsh.cab.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.ride.internal.persistence.RideRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RideEventStreamTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final RideRepository rides = mock(RideRepository.class);
  private final SseEmitter emitter = mock(SseEmitter.class);
  private final RideEventStream stream =
      new RideEventStream(rides, Duration.ofMinutes(1), 1, 1, ignored -> emitter);

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void authorizesParticipantsAndStaffWithoutLeakingLocation() throws IOException {
    Ride ride = ride();
    context(TenantRole.RIDER);
    when(rides.findOwn(TENANT, ACCOUNT, ride.id())).thenReturn(Optional.of(ride));
    stream.subscribe(ride.id());
    assertEquals(1, stream.subscriberCount(TENANT, ride.id()));

    stream.publish(TENANT,
        new RideEventStream.RideStatusEvent(ride.id(), RideStatus.DRIVER_ASSIGNED, 2, NOW));
    verify(emitter, times(2))
        .send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
    verify(rides, never()).findAssignedToDriver(TENANT, ACCOUNT, ride.id());

    assertThrows(ConflictException.class, () -> stream.subscribe(ride.id()));
  }

  @Test
  void deniesUnsupportedRolesAndHidesUnownedRides() {
    Ride ride = ride();
    context(TenantRole.FINANCE);
    assertThrows(TenantAccessDeniedException.class, () -> stream.subscribe(ride.id()));

    context(TenantRole.DRIVER);
    assertThrows(NotFoundException.class, () -> stream.subscribe(ride.id()));
  }

  @Test
  void publishesOnlyAfterTransactionCommit() throws IOException {
    Ride ride = ride();
    context(TenantRole.TENANT_ADMIN);
    when(rides.find(TENANT, ride.id())).thenReturn(Optional.of(ride));
    stream.subscribe(ride.id());
    org.mockito.Mockito.clearInvocations(emitter);
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    stream.afterCommit(TENANT, ride);
    verify(emitter, never()).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
    TransactionSynchronization synchronization =
        TransactionSynchronizationManager.getSynchronizations().get(0);
    synchronization.afterCommit();
    verify(emitter).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void resumeSubscriptionAlwaysStartsWithCurrentSnapshot() throws IOException {
    Ride ride = ride();
    context(TenantRole.TENANT_ADMIN);
    when(rides.find(TENANT, ride.id())).thenReturn(Optional.of(ride));

    stream.subscribe(ride.id(), 1L);

    verify(emitter).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
    assertEquals(1, stream.subscriberCount(TENANT, ride.id()));
  }

  private Ride ride() {
    return new Ride(UUID.randomUUID(), ACCOUNT, UUID.randomUUID(), UUID.randomUUID(),
        new GeoPoint(12.95, 77.6), new GeoPoint(13.0, 77.65), 100, "USD",
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), RideStatus.DRIVER_ASSIGNED,
        null, null, NOW, NOW, null, null, null, null, NOW, 2);
  }

  private void context(TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, ACCOUNT, UUID.randomUUID(), Set.of(role)));
  }
}

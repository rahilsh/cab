package in.rsh.cab.ride;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.ride.internal.persistence.RideRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RideEventStream {

  private static final Set<TenantRole> STAFF =
      Set.of(TenantRole.TENANT_ADMIN, TenantRole.DISPATCHER, TenantRole.SUPPORT);
  private final RideRepository rides;
  private final long timeoutMillis;
  private final int maxPerActor;
  private final int maxPerRide;
  private final LongFunction<SseEmitter> emitterFactory;
  private final Map<ActorKey, Set<Subscription>> byActor = new HashMap<>();
  private final Map<RideKey, Set<Subscription>> byRide = new HashMap<>();

  @Autowired
  public RideEventStream(
      RideRepository rides,
      @Value("${rides.events.timeout:PT5M}") Duration timeout,
      @Value("${rides.events.max-per-actor:3}") int maxPerActor,
      @Value("${rides.events.max-per-ride:20}") int maxPerRide) {
    this(rides, timeout, maxPerActor, maxPerRide, SseEmitter::new);
  }

  RideEventStream(
      RideRepository rides, Duration timeout, int maxPerActor, int maxPerRide,
      LongFunction<SseEmitter> emitterFactory) {
    this.rides = rides;
    this.timeoutMillis = timeout.toMillis();
    this.maxPerActor = maxPerActor;
    this.maxPerRide = maxPerRide;
    this.emitterFactory = emitterFactory;
  }

  @Transactional(readOnly = true)
  public SseEmitter subscribe(UUID rideId) {
    TenantContext context = TenantContext.require();
    authorize(context, rideId);
    ActorKey actorKey = new ActorKey(context.tenantId(), context.accountId());
    RideKey rideKey = new RideKey(context.tenantId(), rideId);
    SseEmitter emitter = emitterFactory.apply(timeoutMillis);
    Subscription subscription = new Subscription(actorKey, rideKey, emitter);
    synchronized (this) {
      if (byActor.getOrDefault(actorKey, Set.of()).size() >= maxPerActor
          || byRide.getOrDefault(rideKey, Set.of()).size() >= maxPerRide) {
        throw new ConflictException("Too many active ride event streams");
      }
      byActor.computeIfAbsent(actorKey, ignored -> new HashSet<>()).add(subscription);
      byRide.computeIfAbsent(rideKey, ignored -> new HashSet<>()).add(subscription);
    }
    emitter.onCompletion(() -> remove(subscription));
    emitter.onTimeout(() -> remove(subscription));
    emitter.onError(ignored -> remove(subscription));
    try {
      emitter.send(SseEmitter.event().name("ready").comment("ride status stream ready"));
    } catch (IOException exception) {
      remove(subscription);
      throw new IllegalStateException("Could not open ride event stream", exception);
    }
    return emitter;
  }

  public void afterCommit(UUID tenantId, Ride ride) {
    RideStatusEvent event =
        new RideStatusEvent(ride.id(), ride.status(), ride.version(), ride.updatedAt());
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      publish(tenantId, event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            publish(tenantId, event);
          }
        });
  }

  void publish(UUID tenantId, RideStatusEvent event) {
    RideKey key = new RideKey(tenantId, event.rideId());
    ArrayList<Subscription> subscribers;
    synchronized (this) {
      subscribers = new ArrayList<>(byRide.getOrDefault(key, Set.of()));
    }
    for (Subscription subscription : subscribers) {
      try {
        subscription.emitter().send(
            SseEmitter.event().name("ride-status").id(Long.toString(event.version())).data(event));
      } catch (IOException | IllegalStateException exception) {
        remove(subscription);
      }
    }
  }

  synchronized int subscriberCount(UUID tenantId, UUID rideId) {
    return byRide.getOrDefault(new RideKey(tenantId, rideId), Set.of()).size();
  }

  private void authorize(TenantContext context, UUID rideId) {
    boolean staff = context.roles().stream().anyMatch(STAFF::contains);
    boolean visible;
    if (staff) {
      visible = rides.find(context.tenantId(), rideId).isPresent();
    } else {
      boolean rider = context.roles().contains(TenantRole.RIDER);
      boolean driver = context.roles().contains(TenantRole.DRIVER);
      if (!rider && !driver) {
        throw new TenantAccessDeniedException("Ride event stream access is restricted");
      }
      visible = rider && rides.findOwn(context.tenantId(), context.accountId(), rideId).isPresent();
      if (!visible && driver) {
        visible = rides.findAssignedToDriver(context.tenantId(), context.accountId(), rideId).isPresent();
      }
    }
    if (!visible) {
      throw new NotFoundException("Ride not found");
    }
  }

  private synchronized void remove(Subscription subscription) {
    remove(byActor, subscription.actorKey(), subscription);
    remove(byRide, subscription.rideKey(), subscription);
  }

  private <K> void remove(Map<K, Set<Subscription>> index, K key, Subscription subscription) {
    Set<Subscription> subscriptions = index.get(key);
    if (subscriptions != null) {
      subscriptions.remove(subscription);
      if (subscriptions.isEmpty()) {
        index.remove(key);
      }
    }
  }

  public record RideStatusEvent(
      UUID rideId, RideStatus status, long version, Instant occurredAt) {}

  private record ActorKey(UUID tenantId, UUID accountId) {}

  private record RideKey(UUID tenantId, UUID rideId) {}

  private record Subscription(ActorKey actorKey, RideKey rideKey, SseEmitter emitter) {}
}

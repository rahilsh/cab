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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Service
public class RideEventStream {

  private static final Logger log = LoggerFactory.getLogger(RideEventStream.class);
  private static final Set<TenantRole> STAFF =
      Set.of(TenantRole.TENANT_ADMIN, TenantRole.DISPATCHER, TenantRole.SUPPORT);
  private final RideRepository rides;
  private final long timeoutMillis;
  private final int maxPerActor;
  private final int maxPerRide;
  private final LongFunction<SseEmitter> emitterFactory;
  private final RideStreamRedisPublisher publisher;
  private final Map<ActorKey, Set<Subscription>> byActor = new HashMap<>();
  private final Map<RideKey, Set<Subscription>> byRide = new HashMap<>();

  @Autowired
  public RideEventStream(
      RideRepository rides,
      @Value("${rides.events.timeout:PT5M}") Duration timeout,
      @Value("${rides.events.max-per-actor:3}") int maxPerActor,
      @Value("${rides.events.max-per-ride:20}") int maxPerRide,
      RideStreamRedisPublisher publisher) {
    this(rides, timeout, maxPerActor, maxPerRide, SseEmitter::new, publisher);
  }

  RideEventStream(
      RideRepository rides, Duration timeout, int maxPerActor, int maxPerRide,
      LongFunction<SseEmitter> emitterFactory, RideStreamRedisPublisher publisher) {
    this.rides = rides;
    this.timeoutMillis = timeout.toMillis();
    this.maxPerActor = maxPerActor;
    this.maxPerRide = maxPerRide;
    this.emitterFactory = emitterFactory;
    this.publisher = publisher;
  }

  @Transactional(readOnly = true)
  public SseEmitter subscribe(UUID rideId, Long lastEventId) {
    TenantContext context = TenantContext.require();
    visibleRide(context, rideId);
    ActorKey actorKey = new ActorKey(context.tenantId(), context.accountId());
    RideKey rideKey = new RideKey(context.tenantId(), rideId);
    SseEmitter emitter = emitterFactory.apply(timeoutMillis);
    Subscription subscription = new Subscription(actorKey, rideKey, emitter);
    emitter.onCompletion(() -> remove(subscription));
    emitter.onTimeout(() -> remove(subscription));
    emitter.onError(ignored -> remove(subscription));
    synchronized (this) {
      if (byActor.getOrDefault(actorKey, Set.of()).size() >= maxPerActor
          || byRide.getOrDefault(rideKey, Set.of()).size() >= maxPerRide) {
        throw new ConflictException("Too many active ride event streams");
      }
      Ride current = visibleRide(context, rideId);
      try {
        // Always establish the stream with a full current snapshot. A newer version than the
        // resume hint therefore also supplies the minimal Last-Event-ID catch-up behavior.
        if (lastEventId != null && current.version() <= lastEventId) {
          emitter.send(SseEmitter.event().comment("current ride version already observed"));
        }
        emitter.send(statusEvent(current));
        byActor.computeIfAbsent(actorKey, ignored -> new HashSet<>()).add(subscription);
        byRide.computeIfAbsent(rideKey, ignored -> new HashSet<>()).add(subscription);
      } catch (IOException | IllegalStateException exception) {
        remove(subscription);
        throw new IllegalStateException("Could not open ride event stream", exception);
      }
    }
    return emitter;
  }

  public SseEmitter subscribe(UUID rideId) {
    return subscribe(rideId, null);
  }

  public void afterCommit(UUID tenantId, UUID eventId, Ride ride) {
    RideStatusEvent event =
        new RideStatusEvent(eventId, ride.id(), ride.status(), ride.version(), ride.updatedAt());
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      publishRedisBestEffort(tenantId, event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            publishRedisBestEffort(tenantId, event);
          }
        });
  }

  public void receive(String message, ObjectMapper json) {
    try {
      RideStreamRedisPublisher.RideStreamMessage decoded =
          json.readValue(message, RideStreamRedisPublisher.RideStreamMessage.class);
      publish(decoded.tenantId(), decoded.event());
    } catch (RuntimeException exception) {
      log.warn("Discarding invalid ride stream Redis message", exception);
    }
  }

  void publish(UUID tenantId, RideStatusEvent event) {
    RideKey key = new RideKey(tenantId, event.rideId());
    ArrayList<Subscription> subscribers;
    synchronized (this) {
      subscribers = new ArrayList<>(byRide.getOrDefault(key, Set.of()));
    }
    for (Subscription subscription : subscribers) {
      try {
        subscription.emitter().send(statusEvent(event));
      } catch (IOException | IllegalStateException exception) {
        remove(subscription);
      }
    }
  }

  synchronized int subscriberCount(UUID tenantId, UUID rideId) {
    return byRide.getOrDefault(new RideKey(tenantId, rideId), Set.of()).size();
  }

  private Ride visibleRide(TenantContext context, UUID rideId) {
    boolean staff = context.roles().stream().anyMatch(STAFF::contains);
    Ride visible = null;
    if (staff) {
      visible = rides.find(context.tenantId(), rideId).orElse(null);
    } else {
      boolean rider = context.roles().contains(TenantRole.RIDER);
      boolean driver = context.roles().contains(TenantRole.DRIVER);
      if (!rider && !driver) {
        throw new TenantAccessDeniedException("Ride event stream access is restricted");
      }
      if (rider) {
        visible = rides.findOwn(context.tenantId(), context.accountId(), rideId).orElse(null);
      }
      if (visible == null && driver) {
        visible = rides.findAssignedToDriver(context.tenantId(), context.accountId(), rideId)
            .orElse(null);
      }
    }
    if (visible == null) {
      throw new NotFoundException("Ride not found");
    }
    return visible;
  }

  private synchronized void remove(Subscription subscription) {
    remove(byActor, subscription.actorKey(), subscription);
    remove(byRide, subscription.rideKey(), subscription);
  }

  private SseEmitter.SseEventBuilder statusEvent(Ride ride) {
    return statusEvent(new RideStatusEvent(
        null, ride.id(), ride.status(), ride.version(), ride.updatedAt()));
  }

  private SseEmitter.SseEventBuilder statusEvent(RideStatusEvent event) {
    return SseEmitter.event().name("ride-status").id(Long.toString(event.version())).data(event);
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

  private void publishRedisBestEffort(UUID tenantId, RideStatusEvent event) {
    try {
      publisher.publish(tenantId, event);
    } catch (RuntimeException exception) {
      log.warn("Immediate ride stream publish failed; outbox will retry event={}", event.eventId(),
          exception);
    }
  }

  public record RideStatusEvent(
      UUID eventId, UUID rideId, RideStatus status, long version, Instant occurredAt) {}

  private record ActorKey(UUID tenantId, UUID accountId) {}

  private record RideKey(UUID tenantId, UUID rideId) {}

  private record Subscription(ActorKey actorKey, RideKey rideKey, SseEmitter emitter) {}
}

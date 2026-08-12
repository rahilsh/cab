package in.rsh.cab.operations;

import in.rsh.cab.ride.RideEventStream;
import in.rsh.cab.ride.RideStatus;
import in.rsh.cab.ride.RideStreamRedisPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class RideStreamOutboxConsumer implements OutboxConsumer {

  private final RideStreamRedisPublisher publisher;

  public RideStreamOutboxConsumer(RideStreamRedisPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public boolean process(OutboxEvent event) {
    if (!"ride".equals(event.aggregateType()) || !event.payload().hasNonNull("status")) {
      return false;
    }
    RideStatus status = RideStatus.valueOf(event.payload().get("status").asText());
    publisher.publish(event.tenantId(), new RideEventStream.RideStatusEvent(
        event.id(), event.aggregateId(), status, event.aggregateVersion(), event.occurredAt()));
    return true;
  }
}

package in.rsh.cab.ride;

import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class RideStreamRedisPublisher {

  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  public RideStreamRedisPublisher(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  public void publish(UUID tenantId, RideEventStream.RideStatusEvent event) {
    try {
      redis.convertAndSend(channel(tenantId), json.writeValueAsString(
          new RideStreamMessage(tenantId, event)));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Ride stream event cannot be serialized", exception);
    }
  }

  static String channel(UUID tenantId) {
    return "cab:{" + tenantId + "}:ride-events";
  }

  public record RideStreamMessage(UUID tenantId, RideEventStream.RideStatusEvent event) {}
}

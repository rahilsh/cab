package in.rsh.cab.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class RideStreamRedisPublisherTest {

  @Test
  void serializesSafeEventToTenantChannel() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ObjectMapper json = new ObjectMapper();
    RideEventStream.RideStatusEvent event = new RideEventStream.RideStatusEvent(eventId,
        UUID.randomUUID(), RideStatus.IN_PROGRESS, 5, Instant.parse("2026-08-09T10:00:00Z"));

    new RideStreamRedisPublisher(redis, json).publish(tenant, event);

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(redis).convertAndSend(eq(RideStreamRedisPublisher.channel(tenant)), payload.capture());
    RideStreamRedisPublisher.RideStreamMessage decoded = json.readValue(
        payload.getValue(), RideStreamRedisPublisher.RideStreamMessage.class);
    assertEquals(tenant, decoded.tenantId());
    assertEquals(event, decoded.event());
  }
}

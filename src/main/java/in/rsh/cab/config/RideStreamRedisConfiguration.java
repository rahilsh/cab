package in.rsh.cab.config;

import in.rsh.cab.ride.RideEventStream;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RideStreamRedisConfiguration {

  @Bean
  RedisMessageListenerContainer rideStreamRedisListeners(
      RedisConnectionFactory connectionFactory, RideEventStream stream, ObjectMapper json) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(
        (message, pattern) ->
            stream.receive(new String(message.getBody(), StandardCharsets.UTF_8), json),
        new PatternTopic("cab:{*}:ride-events"));
    return container;
  }
}

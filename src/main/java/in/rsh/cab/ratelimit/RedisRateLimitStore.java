package in.rsh.cab.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRateLimitStore implements RateLimitStore {

  private static final DefaultRedisScript<List> CONSUME =
      new DefaultRedisScript<>(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
          return {count, redis.call('PTTL', KEYS[1])}
          """,
          List.class);

  private final StringRedisTemplate redis;

  public RedisRateLimitStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public RateLimitDecision consume(String key, long limit, Duration window) {
    List<?> result =
        redis.execute(CONSUME, List.of(key), Long.toString(Math.max(1, window.toMillis())));
    if (result == null || result.size() != 2) {
      throw new IllegalStateException("Redis did not return a rate limit result");
    }
    long count = ((Number) result.get(0)).longValue();
    long ttlMillis = Math.max(1, ((Number) result.get(1)).longValue());
    return new RateLimitDecision(
        count <= limit, Math.max(0, limit - count), Math.max(1, (ttlMillis + 999) / 1000));
  }
}

package in.rsh.cab.ratelimit;

import java.time.Duration;

public interface RateLimitStore {

  RateLimitDecision consume(String key, long limit, Duration window);
}

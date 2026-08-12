package in.rsh.cab.ratelimit;

import in.rsh.cab.tenancy.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

  private final RateLimitStore store;
  private final boolean enabled;
  private final long limit;
  private final Duration window;
  private final Counter allowed;
  private final Counter rejected;

  public RateLimitInterceptor(
      RateLimitStore store,
      MeterRegistry meters,
      @Value("${rate-limit.enabled:true}") boolean enabled,
      @Value("${rate-limit.requests:120}") long limit,
      @Value("${rate-limit.window:PT1M}") Duration window) {
    if (limit < 1 || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("Rate limit and window must be positive");
    }
    this.store = store;
    this.enabled = enabled;
    this.limit = limit;
    this.window = window;
    this.allowed =
        Counter.builder("cab.rate_limit.requests").tag("outcome", "allowed").register(meters);
    this.rejected =
        Counter.builder("cab.rate_limit.requests").tag("outcome", "rejected").register(meters);
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!enabled) {
      return true;
    }
    TenantContext context = TenantContext.require();
    String key = "cab:{" + context.tenantId() + "}:rate-limit:actor:" + context.accountId();
    RateLimitDecision decision = store.consume(key, limit, window);
    response.setHeader("RateLimit-Limit", Long.toString(limit));
    response.setHeader("RateLimit-Remaining", Long.toString(decision.remaining()));
    response.setHeader("RateLimit-Reset", Long.toString(decision.retryAfterSeconds()));
    if (!decision.allowed()) {
      rejected.increment();
      throw new RateLimitExceededException(decision.retryAfterSeconds());
    }
    allowed.increment();
    return true;
  }
}

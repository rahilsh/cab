package in.rsh.cab.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.rsh.cab.tenancy.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitInterceptorTest {

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void limitsByTenantAndActorAndPublishesHeadersAndMetrics() {
    InMemoryRateLimitStore store = new InMemoryRateLimitStore();
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    RateLimitInterceptor interceptor =
        new RateLimitInterceptor(store, meters, true, 1, Duration.ofMinutes(1));
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    TenantContext.set(new TenantContext(tenantId, actorId, UUID.randomUUID(), Set.of()));
    MockHttpServletResponse first = new MockHttpServletResponse();

    assertTrue(interceptor.preHandle(new MockHttpServletRequest(), first, new Object()));
    assertEquals("1", first.getHeader("RateLimit-Limit"));
    assertEquals("0", first.getHeader("RateLimit-Remaining"));
    RateLimitExceededException exception =
        assertThrows(
            RateLimitExceededException.class,
            () ->
                interceptor.preHandle(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));

    assertTrue(exception.retryAfterSeconds() > 0);
    assertEquals(1, meters.counter("cab.rate_limit.requests", "outcome", "allowed").count());
    assertEquals(1, meters.counter("cab.rate_limit.requests", "outcome", "rejected").count());
    assertTrue(store.lastKey.contains(tenantId.toString()));
    assertTrue(store.lastKey.contains(actorId.toString()));
  }

  @Test
  void canBeDisabled() {
    RateLimitInterceptor interceptor =
        new RateLimitInterceptor(
            (key, limit, window) -> {
              throw new AssertionError("store should not be called");
            },
            new SimpleMeterRegistry(),
            false,
            1,
            Duration.ofSeconds(1));

    assertTrue(
        interceptor.preHandle(
            new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
  }

  private static final class InMemoryRateLimitStore implements RateLimitStore {
    private int count;
    private String lastKey;

    @Override
    public RateLimitDecision consume(String key, long limit, Duration window) {
      lastKey = key;
      count++;
      return new RateLimitDecision(count <= limit, Math.max(0, limit - count), 60);
    }
  }
}

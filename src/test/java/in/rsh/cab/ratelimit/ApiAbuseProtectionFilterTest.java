package in.rsh.cab.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiAbuseProtectionFilterTest {

  @Test
  void limitsPreTenantRequestsByIp() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    RateLimitStore store =
        (key, limit, window) -> {
          int call = calls.incrementAndGet();
          assertTrue(key.contains("ip:203.0.113.8"));
          return new RateLimitDecision(call == 1, 0, 30);
        };
    ApiAbuseProtectionFilter filter = filter(store, 1024, 512);
    MockHttpServletRequest request = request("/api/v1/tenants", 0);

    MockHttpServletResponse first = new MockHttpServletResponse();
    filter.doFilter(request, first, new MockFilterChain());
    assertEquals(200, first.getStatus());

    MockHttpServletResponse rejected = new MockHttpServletResponse();
    filter.doFilter(request, rejected, new MockFilterChain());
    assertEquals(429, rejected.getStatus());
    assertTrue(rejected.getContentAsString().contains("rate-limit-exceeded"));
  }

  @Test
  void callbackUsesSecondAccountIpBucket() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    RateLimitStore store =
        (key, limit, window) -> {
          int call = calls.incrementAndGet();
          if (call == 2) {
            assertTrue(key.contains("cab:callback:account-1:203.0.113.8"));
            return new RateLimitDecision(false, 0, 10);
          }
          return new RateLimitDecision(true, 1, 10);
        };
    MockHttpServletRequest request =
        request("/api/v1/payment-providers/fake/accounts/account-1/events", 10);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter(store, 1024, 512).doFilter(request, response, new MockFilterChain());

    assertEquals(429, response.getStatus());
    assertEquals(2, calls.get());
  }

  @Test
  void rejectsOversizedJsonAndCallbackBeforeRateLimitOrChain() throws Exception {
    RateLimitStore store =
        (key, limit, window) -> {
          throw new AssertionError("Rate limiter should not be called");
        };
    for (String path :
        new String[] {"/api/v1/tenants", "/api/v1/payment-providers/fake/accounts/id/events"}) {
      MockHttpServletRequest request = request(path, 2048);
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter(store, 1024, 512).doFilter(request, response, chain);

      assertEquals(413, response.getStatus());
      assertTrue(response.getContentAsString().contains("payload-too-large"));
      assertFalse(chain.getRequest() != null);
    }
  }

  @Test
  void excludesHealth() throws Exception {
    ApiAbuseProtectionFilter filter = filter((key, limit, window) -> null, 1024, 512);
    MockHttpServletRequest request = request("/actuator/health/readiness", 0);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.getRequest() != null);
  }

  private ApiAbuseProtectionFilter filter(
      RateLimitStore store, long maxRequestBytes, long maxCallbackBytes) {
    return new ApiAbuseProtectionFilter(
        store, true, 10, 5, Duration.ofMinutes(1), maxRequestBytes, maxCallbackBytes);
  }

  private MockHttpServletRequest request(String path, long contentLength) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr("203.0.113.8");
    request.setContent(new byte[(int) contentLength]);
    return request;
  }
}

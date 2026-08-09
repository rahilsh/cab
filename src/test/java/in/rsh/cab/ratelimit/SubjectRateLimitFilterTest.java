package in.rsh.cab.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SubjectRateLimitFilterTest {

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void limitsAuthenticatedApiSubject() throws Exception {
    SubjectRateLimitFilter filter =
        new SubjectRateLimitFilter(
            (key, limit, window) -> {
              assertEquals("cab:pre-tenant:subject:subject", key);
              return new RateLimitDecision(false, 0, 20);
            },
            true,
            10,
            Duration.ofMinutes(1));
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("subject").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/tenants"), response, new MockFilterChain());

    assertEquals(429, response.getStatus());
    assertEquals("20", response.getHeader("Retry-After"));
    assertTrue(response.getContentAsString().contains("rate-limit-exceeded"));
  }

  @Test
  void excludesHealthAndUnauthenticatedRequests() throws Exception {
    SubjectRateLimitFilter filter =
        new SubjectRateLimitFilter(
            (key, limit, window) -> {
              throw new AssertionError("store should not be called");
            },
            true,
            10,
            Duration.ofMinutes(1));
    for (String path : new String[] {"/actuator/health/readiness", "/api/v1/tenants"}) {
      MockFilterChain chain = new MockFilterChain();
      filter.doFilter(
          new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
      assertTrue(chain.getRequest() != null);
    }
  }
}

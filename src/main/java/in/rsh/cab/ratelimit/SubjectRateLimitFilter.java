package in.rsh.cab.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SubjectRateLimitFilter extends OncePerRequestFilter {

  private final RateLimitStore store;
  private final boolean enabled;
  private final long limit;
  private final Duration window;

  public SubjectRateLimitFilter(
      RateLimitStore store,
      @Value("${rate-limit.enabled:true}") boolean enabled,
      @Value("${rate-limit.pre-tenant-requests:240}") long limit,
      @Value("${rate-limit.window:PT1M}") Duration window) {
    if (limit < 1 || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("Subject rate limit and window must be positive");
    }
    this.store = store;
    this.enabled = enabled;
    this.limit = limit;
    this.window = window;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (enabled && authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      RateLimitDecision decision =
          store.consume("cab:pre-tenant:subject:" + jwt.getSubject(), limit, window);
      if (!decision.allowed()) {
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setStatus(429);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response
            .getWriter()
            .write(
                "{\"type\":\"https://github.com/rahilsh/cab/problems/rate-limit-exceeded\","
                    + "\"title\":\"Too many requests\",\"status\":429,"
                    + "\"detail\":\"Rate limit exceeded\",\"code\":\"rate-limit-exceeded\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}

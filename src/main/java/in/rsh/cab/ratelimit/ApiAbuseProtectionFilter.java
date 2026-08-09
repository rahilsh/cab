package in.rsh.cab.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiAbuseProtectionFilter extends OncePerRequestFilter {

  private static final Pattern CALLBACK =
      Pattern.compile("^/api/v1/payment-providers/[^/]+/accounts/([^/]+)/events$");

  private final RateLimitStore store;
  private final boolean enabled;
  private final long preTenantLimit;
  private final long callbackLimit;
  private final Duration window;
  private final long maxRequestBytes;
  private final long maxCallbackBytes;

  public ApiAbuseProtectionFilter(
      RateLimitStore store,
      @Value("${rate-limit.enabled:true}") boolean enabled,
      @Value("${rate-limit.pre-tenant-requests:240}") long preTenantLimit,
      @Value("${rate-limit.callback-requests:60}") long callbackLimit,
      @Value("${rate-limit.window:PT1M}") Duration window,
      @Value("${request-limits.max-bytes:1048576}") long maxRequestBytes,
      @Value("${request-limits.callback-max-bytes:262144}") long maxCallbackBytes) {
    if (preTenantLimit < 1
        || callbackLimit < 1
        || window.isZero()
        || window.isNegative()
        || maxRequestBytes < 1
        || maxCallbackBytes < 1) {
      throw new IllegalArgumentException("API limits must be positive");
    }
    this.store = store;
    this.enabled = enabled;
    this.preTenantLimit = preTenantLimit;
    this.callbackLimit = callbackLimit;
    this.window = window;
    this.maxRequestBytes = maxRequestBytes;
    this.maxCallbackBytes = maxCallbackBytes;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    Matcher callback = CALLBACK.matcher(path);
    long maximum = callback.matches() ? maxCallbackBytes : maxRequestBytes;
    if (request.getContentLengthLong() > maximum) {
      writeProblem(
          response,
          413,
          "Payload too large",
          "Request body exceeds the configured limit",
          "payload-too-large");
      return;
    }
    if (enabled) {
      String ip = request.getRemoteAddr();
      if (!consume(response, "cab:pre-tenant:ip:" + ip, preTenantLimit)) {
        return;
      }
      if (callback.matches()
          && !consume(response, "cab:callback:" + callback.group(1) + ":" + ip, callbackLimit)) {
        return;
      }
    }
    try {
      chain.doFilter(new SizeLimitedHttpServletRequest(request, maximum), response);
    } catch (ServletException exception) {
      if (!isPayloadTooLarge(exception)) {
        throw exception;
      }
      writePayloadTooLarge(response);
    } catch (IOException exception) {
      if (!isPayloadTooLarge(exception)) {
        throw exception;
      }
      writePayloadTooLarge(response);
    } catch (RuntimeException exception) {
      if (!isPayloadTooLarge(exception)) {
        throw exception;
      }
      writePayloadTooLarge(response);
    }
  }

  private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
    if (response.isCommitted()) {
      throw new PayloadTooLargeException();
    }
    response.resetBuffer();
    writeProblem(
        response,
        413,
        "Payload too large",
        "Request body exceeds the configured limit",
        "payload-too-large");
  }

  private boolean isPayloadTooLarge(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof PayloadTooLargeException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private boolean consume(HttpServletResponse response, String key, long limit) throws IOException {
    RateLimitDecision decision = store.consume(key, limit, window);
    if (decision.allowed()) {
      return true;
    }
    response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
    writeProblem(response, 429, "Too many requests", "Rate limit exceeded", "rate-limit-exceeded");
    return false;
  }

  private void writeProblem(
      HttpServletResponse response, int status, String title, String detail, String code)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"type\":\"https://github.com/rahilsh/cab/problems/"
                + code
                + "\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + ",\"detail\":\""
                + detail
                + "\",\"code\":\""
                + code
                + "\"}");
  }
}

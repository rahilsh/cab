package in.rsh.cab.web;

import in.rsh.cab.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestContextMdcInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    TenantContext tenant = TenantContext.currentOrNull();
    if (tenant != null) {
      MDC.put("tenantId", tenant.tenantId().toString());
      MDC.put("actorId", tenant.accountId().toString());
      return true;
    }

    Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      MDC.put("actorId", "oidc:" + shortHash(jwt.getIssuer() + ":" + jwt.getSubject()));
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    MDC.remove("tenantId");
    MDC.remove("actorId");
  }

  private String shortHash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}

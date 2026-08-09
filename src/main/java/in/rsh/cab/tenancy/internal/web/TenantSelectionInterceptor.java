package in.rsh.cab.tenancy.internal.web;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantSelectionInterceptor implements HandlerInterceptor {

  public static final String TENANT_HEADER = "X-Tenant-ID";
  private final TenantService tenantService;

  public TenantSelectionInterceptor(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String value = request.getHeader(TENANT_HEADER);
    if (value == null || value.isBlank()) {
      throw new InvalidRequestException(TENANT_HEADER + " header is required");
    }
    UUID tenantId;
    try {
      tenantId = UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException(TENANT_HEADER + " must be a UUID");
    }
    Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    Jwt jwt = (Jwt) authentication.getPrincipal();
    TenantContext.set(
        tenantService.authorize(jwt.getIssuer().toString(), jwt.getSubject(), tenantId));
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
    TenantContext.clear();
  }
}

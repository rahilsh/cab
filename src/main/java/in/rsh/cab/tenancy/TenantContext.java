package in.rsh.cab.tenancy;

import java.util.Set;
import java.util.UUID;

public record TenantContext(
    UUID tenantId, UUID accountId, UUID membershipId, Set<TenantRole> roles) {

  private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

  public TenantContext {
    roles = Set.copyOf(roles);
  }

  public static TenantContext require() {
    TenantContext context = CURRENT.get();
    if (context == null) {
      throw new IllegalStateException("Tenant context is not available");
    }
    return context;
  }

  public static void set(TenantContext context) {
    CURRENT.set(context);
  }

  public static void clear() {
    CURRENT.remove();
  }
}

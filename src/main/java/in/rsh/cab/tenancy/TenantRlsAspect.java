package in.rsh.cab.tenancy;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantRlsAspect {

  private final TenantDatabaseContext databaseContext;

  public TenantRlsAspect(TenantDatabaseContext databaseContext) {
    this.databaseContext = databaseContext;
  }

  @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
  public Object applyTenant(ProceedingJoinPoint invocation) throws Throwable {
    TenantContext context = TenantContext.currentOrNull();
    if (context != null) {
      databaseContext.apply(context.tenantId());
    }
    return invocation.proceed();
  }
}

package in.rsh.cab.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantRlsAspectTest {

  private final TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);
  private final TenantRlsAspect aspect = new TenantRlsAspect(databaseContext);
  private final ProceedingJoinPoint invocation = mock(ProceedingJoinPoint.class);

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void appliesSelectedTenantBeforeTransactionBody() throws Throwable {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(
        new TenantContext(
            tenantId, UUID.randomUUID(), UUID.randomUUID(), Set.of(TenantRole.RIDER)));
    when(invocation.proceed()).thenReturn("result");

    assertEquals("result", aspect.applyTenant(invocation));
    verify(databaseContext).apply(tenantId);
    verify(invocation).proceed();
  }

  @Test
  void allowsControlPlaneTransactionWithoutTenantSetting() throws Throwable {
    when(invocation.proceed()).thenReturn("control-plane");

    assertEquals("control-plane", aspect.applyTenant(invocation));
    verify(databaseContext, never()).apply(org.mockito.ArgumentMatchers.any());
  }
}

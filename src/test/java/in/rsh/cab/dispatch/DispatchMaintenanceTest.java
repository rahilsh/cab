package in.rsh.cab.dispatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.location.DriverLocationReconciler;
import in.rsh.cab.tenancy.TenantExecution;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchMaintenanceTest {

  @Test
  void maintainsEveryActiveTenantAndIsolatesFailures() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    TenantRepository tenants = mock(TenantRepository.class);
    TenantExecution execution = mock(TenantExecution.class);
    DispatchService dispatch = mock(DispatchService.class);
    DriverLocationReconciler locations = mock(DriverLocationReconciler.class);
    when(tenants.findActiveIds()).thenReturn(List.of(first, second));
    doThrow(new IllegalStateException("tenant unavailable"))
        .doAnswer(invocation -> {
          ((Runnable) invocation.getArgument(1)).run();
          return null;
        })
        .when(execution).inTransaction(any(UUID.class), any(Runnable.class));

    new DispatchMaintenance(tenants, execution, dispatch, locations, 50).maintain();

    verify(execution).inTransaction(eq(first), org.mockito.ArgumentMatchers.any(Runnable.class));
    verify(execution).inTransaction(eq(second), org.mockito.ArgumentMatchers.any(Runnable.class));
    verify(dispatch).sweepExpired(second, 50);
    verify(locations).reconcile(second, 50);
  }
}

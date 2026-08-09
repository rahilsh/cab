package in.rsh.cab.location;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.location.internal.persistence.LocationCheckpointRepository;
import in.rsh.cab.tenancy.TenantExecution;
import in.rsh.cab.tenancy.internal.persistence.TenantRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocationRetentionTest {

  @Test
  void purgesEachActiveTenantAtConfiguredCutoff() {
    UUID tenant = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-09T10:00:00Z");
    TenantRepository tenants = mock(TenantRepository.class);
    TenantExecution execution = mock(TenantExecution.class);
    LocationCheckpointRepository checkpoints = mock(LocationCheckpointRepository.class);
    when(tenants.findActiveIds()).thenReturn(List.of(tenant));
    when(execution.inTransaction(org.mockito.ArgumentMatchers.eq(tenant),
        org.mockito.ArgumentMatchers.<java.util.function.Supplier<Integer>>any()))
        .thenAnswer(invocation -> invocation
            .getArgument(1, java.util.function.Supplier.class).get());

    new LocationRetention(tenants, execution, checkpoints,
        Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(30), 500).purge();

    verify(checkpoints).deleteCreatedBefore(tenant, now.minus(Duration.ofDays(30)), 500);
  }

  @Test
  void rejectsNonPositiveRetention() {
    assertThrows(IllegalArgumentException.class, () -> new LocationRetention(
        mock(TenantRepository.class), mock(TenantExecution.class),
        mock(LocationCheckpointRepository.class), Clock.systemUTC(), Duration.ZERO, 1));
  }
}

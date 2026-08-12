package in.rsh.cab.tenancy;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class TenantDatabaseContextTest {

  @Test
  void requiresActiveTransaction() {
    TenantDatabaseContext context = new TenantDatabaseContext(org.mockito.Mockito.mock(JdbcClient.class));

    assertThrows(IllegalStateException.class, () -> context.apply(java.util.UUID.randomUUID()));
  }
}

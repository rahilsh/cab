package in.rsh.cab.tenancy;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TenantDatabaseContext {

  private final JdbcClient jdbc;

  public TenantDatabaseContext(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void apply(UUID tenantId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Tenant database context requires an active transaction");
    }
    jdbc.sql("SELECT set_config('app.tenant_id', :tenantId, true)")
        .param("tenantId", tenantId.toString())
        .query(String.class)
        .single();
  }
}

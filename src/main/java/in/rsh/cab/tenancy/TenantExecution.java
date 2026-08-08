package in.rsh.cab.tenancy;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TenantExecution {

  private final TransactionTemplate transactions;
  private final TenantDatabaseContext databaseContext;

  public TenantExecution(TransactionTemplate transactions, TenantDatabaseContext databaseContext) {
    this.transactions = transactions;
    this.databaseContext = databaseContext;
  }

  public <T> T inTransaction(UUID tenantId, Supplier<T> work) {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(work, "work");
    return transactions.execute(
        status -> {
          databaseContext.apply(tenantId);
          return work.get();
        });
  }

  public void inTransaction(UUID tenantId, Runnable work) {
    inTransaction(
        tenantId,
        () -> {
          work.run();
          return null;
        });
  }
}

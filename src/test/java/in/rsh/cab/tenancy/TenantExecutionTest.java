package in.rsh.cab.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class TenantExecutionTest {

  private final TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);
  private final TenantExecution execution =
      new TenantExecution(new TransactionTemplate(new TestTransactionManager()), databaseContext);

  @Test
  void appliesTenantInsideTransactionBeforeWork() {
    UUID tenantId = UUID.randomUUID();

    assertEquals("value", execution.inTransaction(tenantId, () -> "value"));
    verify(databaseContext).apply(tenantId);
  }

  @Test
  void rejectsMissingTenantAndSupportsVoidWork() {
    UUID tenantId = UUID.randomUUID();
    Runnable work = mock(Runnable.class);

    execution.inTransaction(tenantId, work);
    verify(work).run();
    assertThrows(NullPointerException.class, () -> execution.inTransaction(null, work));
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}

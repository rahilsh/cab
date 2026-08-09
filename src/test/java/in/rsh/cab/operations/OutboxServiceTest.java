package in.rsh.cab.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.operations.internal.persistence.OutboxRepository;
import in.rsh.cab.tenancy.TenantDatabaseContext;
import in.rsh.cab.tenancy.TenantExecution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OutboxServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
  private final OutboxRepository repository = mock(OutboxRepository.class);
  private final OutboxService service =
      new OutboxService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  private final OutboxPoller poller =
      new OutboxPoller(repository, Clock.fixed(NOW, ZoneOffset.UTC), tenantExecution());

  private TenantExecution tenantExecution() {
    return new TenantExecution(
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
              @Override
              protected Object doGetTransaction() {
                return new Object();
              }

              @Override
              protected void doBegin(
                  Object transaction,
                  org.springframework.transaction.TransactionDefinition definition) {}

              @Override
              protected void doCommit(
                  org.springframework.transaction.support.DefaultTransactionStatus status) {}

              @Override
              protected void doRollback(
                  org.springframework.transaction.support.DefaultTransactionStatus status) {}
            }),
        mock(TenantDatabaseContext.class));
  }

  @Test
  void appendsAndLeasesEvents() {
    UUID aggregateId = UUID.randomUUID();
    var payload = new ObjectMapper().createObjectNode().put("safe", true);
    UUID eventId = service.append(TENANT, "quote", aggregateId, 0, "quote.created", 1, payload, null);
    verify(repository).insert(
        eventId, TENANT, "quote", aggregateId, 0, "quote.created", 1, payload,
        NOW, NOW, null, null);

    OutboxEvent event = new OutboxEvent(
        eventId, TENANT, "quote", aggregateId, 0, "quote.created", 1,
        payload, NOW, null, null, 1, UUID.randomUUID());
    when(repository.lease(eq(TENANT), eq(10), eq(NOW), eq(NOW.plusSeconds(30)), any()))
        .thenReturn(List.of(event));
    assertEquals(List.of(event), poller.lease(TENANT, 10, Duration.ofSeconds(30)));
  }

  @Test
  void completesRetryAndPermanentFailureWithBoundedErrors() {
    OutboxEvent event = event();
    poller.published(event);
    verify(repository).markPublished(TENANT, event.id(), event.leaseToken(), NOW);

    poller.retry(event, NOW.plusSeconds(10), "temporary");
    verify(repository).markRetry(
        TENANT, event.id(), event.leaseToken(), NOW.plusSeconds(10), "temporary");

    poller.failed(event, "x".repeat(600));
    verify(repository).markFailed(TENANT, event.id(), event.leaseToken(), "x".repeat(500));
    poller.failed(event, null);
    verify(repository).markFailed(TENANT, event.id(), event.leaseToken(), null);
  }

  @Test
  void validatesLeaseInputs() {
    assertThrows(IllegalArgumentException.class,
        () -> poller.lease(TENANT, 0, Duration.ofSeconds(1)));
    assertThrows(IllegalArgumentException.class,
        () -> poller.lease(TENANT, 1, Duration.ZERO));
  }

  private OutboxEvent event() {
    return new OutboxEvent(UUID.randomUUID(), TENANT, "quote", UUID.randomUUID(), 0,
        "quote.created", 1, new ObjectMapper().createObjectNode(), NOW, null, null, 1,
        UUID.randomUUID());
  }
}

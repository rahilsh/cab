package in.rsh.cab.operations;

import in.rsh.cab.operations.internal.persistence.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import in.rsh.cab.tenancy.TenantExecution;

@Service
public class OutboxPoller {

  private final OutboxRepository events;
  private final Clock clock;
  private final TenantExecution tenantExecution;

  public OutboxPoller(OutboxRepository events, Clock clock, TenantExecution tenantExecution) {
    this.events = events;
    this.clock = clock;
    this.tenantExecution = tenantExecution;
  }

  public List<OutboxEvent> lease(UUID tenantId, int limit, Duration leaseDuration) {
    if (limit < 1 || leaseDuration.isZero() || leaseDuration.isNegative()) {
      throw new IllegalArgumentException("Outbox lease limit and duration must be positive");
    }
    Instant now = clock.instant();
    return tenantExecution.inTransaction(
        tenantId, () -> events.lease(tenantId, limit, now, now.plus(leaseDuration)));
  }

  public void published(UUID tenantId, UUID eventId) {
    tenantExecution.inTransaction(
        tenantId, () -> events.markPublished(tenantId, eventId, clock.instant()));
  }

  public void retry(UUID tenantId, UUID eventId, Instant availableAt, String error) {
    tenantExecution.inTransaction(
        tenantId, () -> events.markRetry(tenantId, eventId, availableAt, sanitizeError(error)));
  }

  public void failed(UUID tenantId, UUID eventId, String error) {
    tenantExecution.inTransaction(
        tenantId, () -> events.markFailed(tenantId, eventId, sanitizeError(error)));
  }

  private String sanitizeError(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= 500 ? error : error.substring(0, 500);
  }
}

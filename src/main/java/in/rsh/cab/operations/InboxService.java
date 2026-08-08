package in.rsh.cab.operations;

import in.rsh.cab.operations.internal.persistence.InboxRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxService {

  private final InboxRepository receipts;
  private final Clock clock;

  public InboxService(InboxRepository receipts, Clock clock) {
    this.receipts = receipts;
    this.clock = clock;
  }

  @Transactional
  public boolean receive(UUID tenantId, String consumer, UUID eventId) {
    if (consumer == null || consumer.isBlank() || consumer.length() > 160) {
      throw new IllegalArgumentException("Inbox consumer must contain 1 to 160 characters");
    }
    return receipts.insert(UUID.randomUUID(), tenantId, consumer, eventId, clock.instant());
  }
}

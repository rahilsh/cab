package in.rsh.cab.operations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.rsh.cab.operations.internal.persistence.InboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxServiceTest {

  @Test
  void reportsFirstReceiptAndDuplicates() {
    InboxRepository repository = mock(InboxRepository.class);
    Instant now = Instant.parse("2026-08-08T12:00:00Z");
    InboxService service = new InboxService(repository, Clock.fixed(now, ZoneOffset.UTC));
    UUID tenant = UUID.randomUUID();
    UUID event = UUID.randomUUID();
    when(repository.insert(any(), any(), any(), any(), any())).thenReturn(true, false);

    assertTrue(service.receive(tenant, "billing", event));
    assertFalse(service.receive(tenant, "billing", event));
    assertThrows(IllegalArgumentException.class, () -> service.receive(tenant, " ", event));
  }
}

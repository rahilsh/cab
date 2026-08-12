package in.rsh.cab.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import in.rsh.cab.operations.OutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

class NotificationRecipientResolverTest {

  @Test
  void unrelatedEventsDoNotQueryTenantData() {
    JdbcClient jdbc = mock(JdbcClient.class);
    NotificationRecipientResolver resolver = new NotificationRecipientResolver(jdbc);
    assertEquals(java.util.List.of(), resolver.resolve(new OutboxEvent(UUID.randomUUID(),
        UUID.randomUUID(), "quote", UUID.randomUUID(), 1, "quote.created", 1,
        new ObjectMapper().createObjectNode(), Instant.now(), null, null, 1, UUID.randomUUID())));
    verify(jdbc, never()).sql(anyString());
  }
}

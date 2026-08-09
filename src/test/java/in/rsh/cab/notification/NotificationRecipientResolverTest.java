package in.rsh.cab.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void safetyEventsRouteThroughRoleAndReporterQuery() {
    JdbcClient jdbc = mock(JdbcClient.class);
    JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
    JdbcClient.MappedQuerySpec query = mock(JdbcClient.MappedQuerySpec.class);
    UUID staff = UUID.randomUUID();
    when(jdbc.sql(org.mockito.ArgumentMatchers.contains("tenant_membership_roles")))
        .thenReturn(statement);
    when(statement.param(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(statement);
    when(statement.query(UUID.class)).thenReturn(query);
    when(query.list()).thenReturn(java.util.List.of(staff));
    OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "safety_incident",
        UUID.randomUUID(), 0, "safety.incident_reported", 1,
        new ObjectMapper().createObjectNode(), Instant.now(), null, null, 1, UUID.randomUUID());

    assertEquals(java.util.List.of(staff), new NotificationRecipientResolver(jdbc).resolve(event));
    verify(statement).param("incidentId", event.aggregateId());
  }
}

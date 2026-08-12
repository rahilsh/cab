package in.rsh.cab.operations.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInboxRepository implements InboxRepository {

  private final JdbcClient jdbc;

  public JdbcInboxRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insert(UUID id, UUID tenantId, String consumer, UUID eventId, Instant receivedAt) {
    return jdbc.sql("""
            INSERT INTO inbox_receipts (id, tenant_id, consumer, event_id, received_at)
            VALUES (:id, :tenantId, :consumer, :eventId, :receivedAt)
            ON CONFLICT (tenant_id, consumer, event_id) DO NOTHING
            """)
        .param("id", id).param("tenantId", tenantId).param("consumer", consumer)
        .param("eventId", eventId).param("receivedAt", Timestamp.from(receivedAt)).update() == 1;
  }
}

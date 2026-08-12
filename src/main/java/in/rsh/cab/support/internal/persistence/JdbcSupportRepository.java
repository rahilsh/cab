package in.rsh.cab.support.internal.persistence;

import in.rsh.cab.support.SupportCase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSupportRepository implements SupportRepository {

  private static final String SELECT = """
      SELECT id, opened_by_account_id, ride_id, subject, state, priority, created_at, updated_at,
             version
      FROM support_cases
      """;
  private final JdbcClient jdbc;

  public JdbcSupportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isRideParticipant(UUID tenantId, UUID rideId, UUID accountId) {
    return jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM rides r LEFT JOIN driver_profiles d
              ON d.tenant_id = r.tenant_id AND d.id = r.driver_id
              WHERE r.tenant_id = :tenantId AND r.id = :rideId
                AND (r.rider_account_id = :accountId OR d.account_id = :accountId))
            """)
        .param("tenantId", tenantId).param("rideId", rideId).param("accountId", accountId)
        .query(Boolean.class).single();
  }

  @Override
  public void insert(UUID tenantId, SupportCase supportCase, SupportCase.Message initialMessage) {
    jdbc.sql("""
            INSERT INTO support_cases
              (id, tenant_id, opened_by_account_id, ride_id, subject, state, priority,
               created_at, updated_at)
            VALUES (:id, :tenantId, :opener, :rideId, :subject, :state, :priority, :now, :now)
            """)
        .param("id", supportCase.id()).param("tenantId", tenantId)
        .param("opener", supportCase.openedByAccountId()).param("rideId", supportCase.rideId())
        .param("subject", supportCase.subject()).param("state", supportCase.state())
        .param("priority", supportCase.priority()).param("now", Timestamp.from(supportCase.createdAt()))
        .update();
    insertMessage(tenantId, supportCase.id(), initialMessage);
  }

  @Override
  public List<SupportCase> findOwn(UUID tenantId, UUID accountId) {
    return jdbc.sql(SELECT + """
            WHERE tenant_id = :tenantId AND opened_by_account_id = :accountId
            ORDER BY updated_at DESC, id
            """)
        .param("tenantId", tenantId).param("accountId", accountId).query(this::map).list().stream()
        .map(supportCase -> withMessages(tenantId, supportCase)).toList();
  }

  @Override
  public List<SupportCase> findAll(UUID tenantId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId ORDER BY updated_at DESC, id")
        .param("tenantId", tenantId).query(this::map).list().stream()
        .map(supportCase -> withMessages(tenantId, supportCase)).toList();
  }

  @Override
  public Optional<SupportCase> find(UUID tenantId, UUID caseId) {
    return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id = :id")
        .param("tenantId", tenantId).param("id", caseId).query(this::map).optional()
        .map(supportCase -> withMessages(tenantId, supportCase));
  }

  @Override
  public void insertMessage(UUID tenantId, UUID caseId, SupportCase.Message message) {
    jdbc.sql("""
            INSERT INTO support_messages
              (id, tenant_id, case_id, author_account_id, body, internal, created_at)
            VALUES (:id, :tenantId, :caseId, :authorId, :body, :internal, :now)
            """)
        .param("id", message.id()).param("tenantId", tenantId).param("caseId", caseId)
        .param("authorId", message.authorAccountId()).param("body", message.body())
        .param("internal", message.internal()).param("now", Timestamp.from(message.createdAt()))
        .update();
  }

  @Override
  public boolean appendMessage(
      UUID tenantId, UUID caseId, long expectedVersion, SupportCase.Message message, Instant now) {
    int claimed = jdbc.sql("""
            UPDATE support_cases SET updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND id = :caseId AND version = :expectedVersion
              AND state IN ('OPEN', 'IN_PROGRESS')
            """)
        .param("now", Timestamp.from(now)).param("tenantId", tenantId).param("caseId", caseId)
        .param("expectedVersion", expectedVersion).update();
    if (claimed != 1) {
      return false;
    }
    insertMessage(tenantId, caseId, message);
    return true;
  }

  @Override
  public boolean updateState(
      UUID tenantId, UUID caseId, String expectedState, String state, long expectedVersion,
      Instant now) {
    return jdbc.sql("""
            UPDATE support_cases
            SET state = :state, updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND id = :id AND state = :expectedState
              AND version = :expectedVersion
            """)
        .param("state", state).param("now", Timestamp.from(now)).param("tenantId", tenantId)
        .param("id", caseId).param("expectedState", expectedState)
        .param("expectedVersion", expectedVersion).update() == 1;
  }

  @Override
  public void appendState(
      UUID tenantId, UUID caseId, String from, String to, UUID actorId, String reason, Instant now) {
    jdbc.sql("""
            INSERT INTO support_case_state_history
              (id, tenant_id, case_id, from_state, to_state, actor_account_id, reason, occurred_at)
            VALUES (:id, :tenantId, :caseId, :fromState, :toState, :actorId, :reason, :now)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId).param("caseId", caseId)
        .param("fromState", from).param("toState", to).param("actorId", actorId)
        .param("reason", reason).param("now", Timestamp.from(now)).update();
  }

  @Override
  public boolean hasStaffRole(UUID tenantId, UUID accountId) {
    return jdbc.sql("""
            SELECT EXISTS (
              SELECT 1 FROM tenant_memberships membership
              JOIN tenant_membership_roles role ON role.membership_id = membership.id
              WHERE membership.tenant_id = :tenantId
                AND membership.user_account_id = :accountId
                AND membership.status = 'ACTIVE'
                AND role.role IN ('SUPPORT', 'TENANT_ADMIN'))
            """)
        .param("tenantId", tenantId).param("accountId", accountId)
        .query(Boolean.class).single();
  }

  @Override
  public boolean assign(
      UUID tenantId, UUID caseId, UUID assigneeId, UUID actorId, long expectedVersion,
      Instant now) {
    int claimed = jdbc.sql("""
            UPDATE support_cases SET updated_at = :now, version = version + 1
            WHERE tenant_id = :tenantId AND id = :caseId AND version = :expectedVersion
            """)
        .param("now", Timestamp.from(now)).param("tenantId", tenantId).param("caseId", caseId)
        .param("expectedVersion", expectedVersion).update();
    if (claimed != 1) {
      return false;
    }
    jdbc.sql("""
            UPDATE support_assignments SET active = false, ended_at = :now
            WHERE tenant_id = :tenantId AND case_id = :caseId AND active
            """)
        .param("now", Timestamp.from(now)).param("tenantId", tenantId).param("caseId", caseId)
        .update();
    jdbc.sql("""
            INSERT INTO support_assignments
              (id, tenant_id, case_id, assignee_account_id, assigned_by_account_id,
               active, assigned_at)
            VALUES (:id, :tenantId, :caseId, :assigneeId, :actorId, true, :now)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId).param("caseId", caseId)
        .param("assigneeId", assigneeId).param("actorId", actorId)
        .param("now", Timestamp.from(now)).update();
    return true;
  }

  private SupportCase map(ResultSet rs, int row) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    return new SupportCase(id, rs.getObject("opened_by_account_id", UUID.class),
        rs.getObject("ride_id", UUID.class), rs.getString("subject"), rs.getString("state"),
        rs.getString("priority"), rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"), List.of());
  }

  private SupportCase withMessages(UUID tenantId, SupportCase supportCase) {
    List<SupportCase.Message> messages = jdbc.sql("""
            SELECT id, author_account_id, body, internal, created_at
            FROM support_messages
            WHERE tenant_id = :tenantId AND case_id = :caseId
            ORDER BY created_at, id
            """)
        .param("tenantId", tenantId).param("caseId", supportCase.id())
        .query((rs, row) -> new SupportCase.Message(rs.getObject("id", UUID.class),
            rs.getObject("author_account_id", UUID.class), rs.getString("body"),
            rs.getBoolean("internal"), rs.getTimestamp("created_at").toInstant()))
        .list();
    return new SupportCase(supportCase.id(), supportCase.openedByAccountId(), supportCase.rideId(),
        supportCase.subject(), supportCase.state(), supportCase.priority(), supportCase.createdAt(),
        supportCase.updatedAt(), supportCase.version(), messages);
  }
}

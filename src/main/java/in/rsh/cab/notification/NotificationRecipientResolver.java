package in.rsh.cab.notification;

import in.rsh.cab.operations.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class NotificationRecipientResolver {

  private final JdbcClient jdbc;

  public NotificationRecipientResolver(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<UUID> resolve(OutboxEvent event) {
    if (event.eventType().startsWith("ride.")) {
      return jdbc.sql("""
              SELECT recipient_id FROM (
                SELECT ride.rider_account_id recipient_id
                FROM rides ride
                WHERE ride.tenant_id = :tenantId AND ride.id = :rideId
                UNION
                SELECT driver.account_id recipient_id
                FROM rides ride JOIN driver_profiles driver
                  ON driver.tenant_id = ride.tenant_id AND driver.id = ride.driver_id
                WHERE ride.tenant_id = :tenantId AND ride.id = :rideId
              ) recipients ORDER BY recipient_id
              """)
          .param("tenantId", event.tenantId()).param("rideId", event.aggregateId())
          .query(UUID.class).list();
    }
    if (event.eventType().startsWith("payment.")) {
      String sql = "refund".equals(event.aggregateType())
          ? """
              SELECT payment.rider_account_id
              FROM refunds refund JOIN payments payment
                ON payment.tenant_id = refund.tenant_id AND payment.id = refund.payment_id
              WHERE refund.tenant_id = :tenantId AND refund.id = :aggregateId
              """
          : """
              SELECT rider_account_id FROM payments
              WHERE tenant_id = :tenantId AND id = :aggregateId
              """;
      return jdbc.sql(sql).param("tenantId", event.tenantId())
          .param("aggregateId", event.aggregateId()).query(UUID.class).list();
    }
    if (event.eventType().startsWith("safety.")) {
      return jdbc.sql("""
              SELECT recipient_id FROM (
                SELECT membership.user_account_id recipient_id
                FROM tenant_memberships membership
                JOIN tenant_membership_roles role ON role.membership_id = membership.id
                WHERE membership.tenant_id = :tenantId AND membership.status = 'ACTIVE'
                  AND role.role IN ('SAFETY', 'TENANT_ADMIN')
                UNION
                SELECT incident.reported_by_account_id recipient_id
                FROM safety_incidents incident
                WHERE incident.tenant_id = :tenantId AND incident.id = :incidentId
              ) recipients ORDER BY recipient_id
              """)
          .param("tenantId", event.tenantId()).param("incidentId", event.aggregateId())
          .query(UUID.class).list();
    }
    return List.of();
  }
}

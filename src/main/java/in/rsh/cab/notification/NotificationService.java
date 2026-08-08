package in.rsh.cab.notification;

import in.rsh.cab.notification.internal.persistence.NotificationRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository notifications;
  private final Clock clock;

  public NotificationService(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional
  public NotificationPreference setPreference(String eventType, String channel, boolean enabled) {
    TenantContext context = requireUser();
    if (!"LOCAL".equals(channel)) {
      throw new IllegalArgumentException("Unsupported notification channel");
    }
    return notifications.upsertPreference(context.tenantId(), context.accountId(), eventType,
        channel, enabled, clock.instant());
  }

  @Transactional(readOnly = true)
  public List<NotificationPreference> preferences() {
    TenantContext context = requireUser();
    return notifications.preferences(context.tenantId(), context.accountId());
  }

  private TenantContext requireUser() {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.RIDER)
        && !context.roles().contains(TenantRole.DRIVER)) {
      throw new TenantAccessDeniedException("RIDER or DRIVER role is required");
    }
    return context;
  }
}

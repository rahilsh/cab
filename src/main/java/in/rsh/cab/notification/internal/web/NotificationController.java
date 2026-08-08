package in.rsh.cab.notification.internal.web;

import in.rsh.cab.notification.NotificationPreference;
import in.rsh.cab.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-preferences")
public class NotificationController {

  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @PutMapping
  public NotificationPreference set(@Valid @RequestBody PreferenceRequest request) {
    return notifications.setPreference(request.eventType(), request.channel(), request.enabled());
  }

  @GetMapping
  public List<NotificationPreference> list() {
    return notifications.preferences();
  }

  public record PreferenceRequest(
      @NotBlank @Size(max = 160) String eventType,
      @NotBlank @Pattern(regexp = "LOCAL") String channel,
      boolean enabled) {}
}

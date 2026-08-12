package in.rsh.cab.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationPreference(
    UUID id, String eventType, String channel, boolean enabled, Instant updatedAt) {}

package in.rsh.cab.tenancy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TenantInvitation(
    UUID id,
    UUID tenantId,
    String email,
    Set<TenantRole> roles,
    String status,
    Instant expiresAt,
    String token) {}

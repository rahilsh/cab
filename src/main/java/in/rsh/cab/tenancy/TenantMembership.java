package in.rsh.cab.tenancy;

import java.util.Set;
import java.util.UUID;

public record TenantMembership(UUID id, UUID tenantId, UUID accountId, Set<TenantRole> roles) {}

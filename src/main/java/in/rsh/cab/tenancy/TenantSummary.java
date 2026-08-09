package in.rsh.cab.tenancy;

import java.util.Set;
import java.util.UUID;

public record TenantSummary(UUID id, String slug, String displayName, String status, Set<TenantRole> roles) {}

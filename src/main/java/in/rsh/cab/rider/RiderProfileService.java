package in.rsh.cab.rider;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.rider.internal.persistence.RiderProfileRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiderProfileService {

  private final RiderProfileRepository profiles;
  private final Clock clock;

  public RiderProfileService(RiderProfileRepository profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }

  @Transactional
  public RiderProfile create(String displayName, String phoneNumber) {
    TenantContext context = requireRider();
    Instant now = clock.instant();
    RiderProfile profile = new RiderProfile(UUID.randomUUID(), displayName, phoneNumber, now, now);
    try {
      profiles.insert(context.tenantId(), context.accountId(), profile);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Rider profile already exists");
    }
    return profile;
  }

  @Transactional(readOnly = true)
  public RiderProfile getOwn() {
    TenantContext context = requireRider();
    return profiles.findByTenantIdAndAccountId(context.tenantId(), context.accountId())
        .orElseThrow(() -> new NotFoundException("Rider profile not found"));
  }

  @Transactional
  public RiderProfile updateOwn(String displayName, String phoneNumber) {
    TenantContext context = requireRider();
    if (!profiles.update(
        context.tenantId(), context.accountId(), displayName, phoneNumber, clock.instant())) {
      throw new NotFoundException("Rider profile not found");
    }
    return getOwn();
  }

  private TenantContext requireRider() {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(TenantRole.RIDER)) {
      throw new TenantAccessDeniedException("RIDER role is required");
    }
    return context;
  }
}

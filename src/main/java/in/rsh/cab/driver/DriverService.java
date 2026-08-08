package in.rsh.cab.driver;

import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.driver.internal.persistence.DriverProfileRepository;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import in.rsh.cab.tenancy.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

  private final DriverProfileRepository drivers;
  private final TenantService tenants;
  private final Clock clock;

  public DriverService(DriverProfileRepository drivers, TenantService tenants, Clock clock) {
    this.drivers = drivers;
    this.tenants = tenants;
    this.clock = clock;
  }

  @Transactional
  public DriverProfile create(UUID accountId, String legalName, String phoneNumber) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    tenants.grantRoleForActiveAccount(accountId, TenantRole.DRIVER);
    Instant now = clock.instant();
    DriverProfile profile =
        new DriverProfile(
            UUID.randomUUID(), accountId, legalName, phoneNumber, DriverStatus.PENDING, now, now);
    try {
      drivers.insert(context.tenantId(), profile);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Driver profile already exists for account");
    }
    return profile;
  }

  @Transactional(readOnly = true)
  public List<DriverProfile> list() {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    return drivers.findAllByTenantId(context.tenantId());
  }

  @Transactional
  public DriverProfile approve(UUID id) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    DriverProfile current = find(context.tenantId(), id);
    DriverProfile approved = current.approve(clock.instant());
    if (!drivers.updateStatus(
        context.tenantId(), id, current.status(), approved.status(), approved.updatedAt())) {
      throw new ConflictException("Driver status changed concurrently");
    }
    return approved;
  }

  @Transactional
  public DriverProfile suspend(UUID id) {
    TenantContext context = require(TenantRole.TENANT_ADMIN);
    DriverProfile current = find(context.tenantId(), id);
    DriverProfile suspended = current.suspend(clock.instant());
    if (!drivers.updateStatus(
        context.tenantId(), id, current.status(), suspended.status(), suspended.updatedAt())) {
      throw new ConflictException("Driver status changed concurrently");
    }
    return suspended;
  }

  @Transactional(readOnly = true)
  public DriverProfile getOwn() {
    TenantContext context = require(TenantRole.DRIVER);
    return drivers.findByTenantIdAndAccountId(context.tenantId(), context.accountId())
        .orElseThrow(() -> new NotFoundException("Driver profile not found"));
  }

  @Transactional
  public DriverProfile updateOwn(String legalName, String phoneNumber) {
    TenantContext context = require(TenantRole.DRIVER);
    if (!drivers.updateOwn(
        context.tenantId(), context.accountId(), legalName, phoneNumber, clock.instant())) {
      throw new NotFoundException("Driver profile not found");
    }
    return getOwn();
  }

  private DriverProfile find(UUID tenantId, UUID id) {
    return drivers.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new NotFoundException("Driver profile not found"));
  }

  private TenantContext require(TenantRole role) {
    TenantContext context = TenantContext.require();
    if (!context.roles().contains(role)) {
      throw new TenantAccessDeniedException(role + " role is required");
    }
    return context;
  }
}

package in.rsh.cab.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.audit.AuditService;
import in.rsh.cab.exception.ConflictException;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.rating.internal.persistence.RatingRepository;
import in.rsh.cab.rating.internal.persistence.RatingRepository.RideParticipants;
import in.rsh.cab.tenancy.TenantAccessDeniedException;
import in.rsh.cab.tenancy.TenantContext;
import in.rsh.cab.tenancy.TenantRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

class RatingServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final UUID RIDE = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private final RatingRepository repository = mock(RatingRepository.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final AuditService audit = mock(AuditService.class);
  private final RatingService service = new RatingService(repository, outbox, audit,
      new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void completedRiderAndDriverCanEachRateTheOther() {
    UUID driver = UUID.randomUUID();
    when(repository.completedRideParticipants(TENANT, RIDE))
        .thenReturn(Optional.of(new RideParticipants(ACCOUNT, driver)));
    context(ACCOUNT, TenantRole.RIDER);
    Rating riderRating = service.create(RIDE, 5, "great");
    assertEquals(driver, riderRating.revieweeAccountId());
    assertEquals("RIDER", riderRating.reviewerRole());
    verify(repository).insert(TENANT, riderRating);
    verify(outbox).append(any(), any(), any(), any(Long.class), any(), any(Integer.class), any(), any());

    context(driver, TenantRole.DRIVER);
    Rating driverRating = service.create(RIDE, 4, null);
    assertEquals(ACCOUNT, driverRating.revieweeAccountId());
    assertEquals("DRIVER", driverRating.reviewerRole());
  }

  @Test
  void rejectsInvalidIncompleteNonParticipantAndDuplicateRatings() {
    context(ACCOUNT, TenantRole.RIDER);
    assertThrows(InvalidRequestException.class, () -> service.create(RIDE, 0, null));
    assertThrows(NotFoundException.class, () -> service.create(RIDE, 3, null));
    when(repository.completedRideParticipants(TENANT, RIDE)).thenReturn(Optional.of(
        new RideParticipants(UUID.randomUUID(), UUID.randomUUID())));
    assertThrows(TenantAccessDeniedException.class, () -> service.create(RIDE, 3, null));

    when(repository.completedRideParticipants(TENANT, RIDE)).thenReturn(Optional.of(
        new RideParticipants(ACCOUNT, UUID.randomUUID())));
    org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
        .when(repository).insert(any(), any());
    assertThrows(ConflictException.class, () -> service.create(RIDE, 3, null));
  }

  private void context(UUID account, TenantRole role) {
    TenantContext.set(new TenantContext(TENANT, account, UUID.randomUUID(), Set.of(role)));
  }
}

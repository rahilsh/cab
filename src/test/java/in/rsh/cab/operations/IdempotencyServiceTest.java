package in.rsh.cab.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.operations.internal.persistence.IdempotencyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IdempotencyServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
  private static final String HASH = "a".repeat(64);
  private final IdempotencyRepository repository = mock(IdempotencyRepository.class);
  private IdempotencyService service;

  @BeforeEach
  void setUp() {
    service = new IdempotencyService(
        repository, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));
  }

  @Test
  void reservesNewRequest() {
    when(repository.insertReservation(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(TENANT),
        org.mockito.ArgumentMatchers.eq(ACTOR), org.mockito.ArgumentMatchers.eq("quote.create"),
        org.mockito.ArgumentMatchers.eq("key"), org.mockito.ArgumentMatchers.eq(HASH),
        org.mockito.ArgumentMatchers.eq(NOW),
        org.mockito.ArgumentMatchers.eq(NOW.plusSeconds(3600)))).thenReturn(true);

    IdempotencyReservation reservation =
        service.reserve(TENANT, ACTOR, "quote.create", "key", HASH);

    assertEquals(IdempotencyReservation.Status.RESERVED, reservation.status());
  }

  @Test
  void replaysCompletedRequest() {
    UUID id = UUID.randomUUID();
    UUID resource = UUID.randomUUID();
    var response = new ObjectMapper().createObjectNode().put("id", resource.toString());
    when(repository.lock(TENANT, ACTOR, "quote.create", "key"))
        .thenReturn(Optional.of(new IdempotencyRecord(
            id, TENANT, ACTOR, "quote.create", "key", HASH, IdempotencyState.COMPLETED,
            resource, 201, response, NOW.plusSeconds(60))));

    IdempotencyReservation reservation =
        service.reserve(TENANT, ACTOR, "quote.create", "key", HASH);

    assertEquals(IdempotencyReservation.Status.REPLAY, reservation.status());
    assertEquals(response, reservation.safeResponse());
    assertEquals(resource, reservation.resourceId());
  }

  @Test
  void rejectsHashReuseAndActiveRequest() {
    when(repository.lock(TENANT, ACTOR, "quote.create", "key"))
        .thenReturn(Optional.of(record(HASH, IdempotencyState.IN_PROGRESS, NOW.plusSeconds(60))));
    IdempotencyConflictException active = assertThrows(
        IdempotencyConflictException.class,
        () -> service.reserve(TENANT, ACTOR, "quote.create", "key", HASH));
    assertEquals(IdempotencyConflictException.Reason.IN_PROGRESS, active.reason());

    IdempotencyConflictException reused = assertThrows(
        IdempotencyConflictException.class,
        () -> service.reserve(TENANT, ACTOR, "quote.create", "key", "b".repeat(64)));
    assertEquals(IdempotencyConflictException.Reason.KEY_REUSED, reused.reason());
  }

  @Test
  void replacesExpiredOrFailedReservationsEvenWhenPayloadChanged() {
    IdempotencyRecord expired = record(HASH, IdempotencyState.COMPLETED, NOW);
    when(repository.lock(TENANT, ACTOR, "quote.create", "expired"))
        .thenReturn(Optional.of(expired));
    assertEquals(IdempotencyReservation.Status.RESERVED,
        service.reserve(TENANT, ACTOR, "quote.create", "expired", "b".repeat(64)).status());

    IdempotencyRecord failed = record(HASH, IdempotencyState.FAILED, NOW.plusSeconds(60));
    when(repository.lock(TENANT, ACTOR, "quote.create", "failed"))
        .thenReturn(Optional.of(failed));
    assertEquals(IdempotencyReservation.Status.RESERVED,
        service.reserve(TENANT, ACTOR, "quote.create", "failed", HASH).status());
  }

  @Test
  void validatesInputsAndCompletionThenSupportsFailure() {
    assertThrows(IllegalArgumentException.class,
        () -> new IdempotencyService(repository, Clock.systemUTC(), Duration.ZERO));
    assertThrows(InvalidRequestException.class,
        () -> service.reserve(TENANT, ACTOR, "", "key", HASH));
    assertThrows(InvalidRequestException.class,
        () -> service.reserve(TENANT, ACTOR, "operation", "", HASH));
    assertThrows(InvalidRequestException.class,
        () -> service.reserve(TENANT, ACTOR, "operation", "key", "INVALID"));
    assertThrows(IllegalArgumentException.class,
        () -> service.complete(TENANT, ACTOR, UUID.randomUUID(), "quote", UUID.randomUUID(),
            500, new ObjectMapper().createObjectNode()));

    UUID recordId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    var response = new ObjectMapper().createObjectNode().put("safe", true);
    service.complete(TENANT, ACTOR, recordId, "quote", resourceId, 201, response);
    verify(repository).complete(TENANT, ACTOR, recordId, "quote", resourceId, 201, response, NOW);
    service.complete(TENANT, ACTOR, recordId, "quote", resourceId, 204, null);
    verify(repository).complete(TENANT, ACTOR, recordId, "quote", resourceId, 204, null, NOW);
    service.fail(TENANT, ACTOR, recordId);
    verify(repository).fail(TENANT, ACTOR, recordId, NOW);
  }

  @Test
  void detectsDisappearingConflict() {
    assertThrows(IllegalStateException.class,
        () -> service.reserve(TENANT, ACTOR, "quote.create", "key", HASH));
  }

  private IdempotencyRecord record(String hash, IdempotencyState state, Instant expiresAt) {
    return new IdempotencyRecord(
        UUID.randomUUID(), TENANT, ACTOR, "quote.create", "key", hash, state,
        null, 0, null, expiresAt);
  }
}

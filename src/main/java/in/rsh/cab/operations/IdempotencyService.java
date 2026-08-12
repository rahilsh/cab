package in.rsh.cab.operations;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.operations.internal.persistence.IdempotencyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class IdempotencyService {

  private final IdempotencyRepository records;
  private final Clock clock;
  private final Duration ttl;

  public IdempotencyService(
      IdempotencyRepository records,
      Clock clock,
      @Value("${idempotency.ttl:PT24H}") Duration ttl) {
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("Idempotency TTL must be positive");
    }
    this.records = records;
    this.clock = clock;
    this.ttl = ttl;
  }

  @Transactional
  public IdempotencyReservation reserve(
      UUID tenantId, UUID actorAccountId, String operation, String key, String requestHash) {
    validate(operation, key, requestHash);
    Instant now = clock.instant();
    UUID id = UUID.randomUUID();
    Instant expiresAt = now.plus(ttl);
    if (records.insertReservation(
        id, tenantId, actorAccountId, operation, key, requestHash, now, expiresAt)) {
      return IdempotencyReservation.reserved(id);
    }

    IdempotencyRecord existing = records.lock(tenantId, actorAccountId, operation, key)
        .orElseThrow(() -> new IllegalStateException("Conflicting idempotency record disappeared"));
    if (!existing.expiresAt().isAfter(now) || existing.state() == IdempotencyState.FAILED) {
      records.replaceReservation(
          tenantId, actorAccountId, existing.id(), id, requestHash, now, expiresAt);
      return IdempotencyReservation.reserved(id);
    }
    if (!existing.requestHash().equals(requestHash)) {
      throw new IdempotencyConflictException(IdempotencyConflictException.Reason.KEY_REUSED);
    }
    if (existing.state() == IdempotencyState.COMPLETED) {
      return IdempotencyReservation.replay(existing);
    }
    throw new IdempotencyConflictException(IdempotencyConflictException.Reason.IN_PROGRESS);
  }

  @Transactional
  public void complete(
      UUID tenantId, UUID actorAccountId, UUID recordId, String resourceType, UUID resourceId,
      int httpStatus, JsonNode safeResponse) {
    if (httpStatus < 200 || httpStatus > 299) {
      throw new IllegalArgumentException("Completed idempotency status must be successful");
    }
    records.complete(
        tenantId, actorAccountId, recordId, resourceType, resourceId, httpStatus,
        safeResponse, clock.instant());
  }

  @Transactional
  public void fail(UUID tenantId, UUID actorAccountId, UUID recordId) {
    records.fail(tenantId, actorAccountId, recordId, clock.instant());
  }

  private void validate(String operation, String key, String requestHash) {
    if (operation == null || operation.isBlank() || operation.length() > 120) {
      throw new InvalidRequestException("Idempotency operation is invalid");
    }
    if (key == null || key.isBlank() || key.length() > 255) {
      throw new InvalidRequestException("Idempotency-Key must contain 1 to 255 characters");
    }
    if (requestHash == null || !requestHash.equals(requestHash.toLowerCase(Locale.ROOT))
        || !requestHash.matches("[0-9a-f]{64}")) {
      throw new InvalidRequestException("Idempotency request hash must be lowercase SHA-256");
    }
  }
}

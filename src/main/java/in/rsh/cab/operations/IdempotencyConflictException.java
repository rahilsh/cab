package in.rsh.cab.operations;

public class IdempotencyConflictException extends RuntimeException {

  private final Reason reason;

  public IdempotencyConflictException(Reason reason) {
    super(reason == Reason.KEY_REUSED
        ? "Idempotency key was already used for a different request"
        : "A request with this idempotency key is still in progress");
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    KEY_REUSED,
    IN_PROGRESS
  }
}

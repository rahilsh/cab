package in.rsh.cab.chain;

public abstract class AbstractValidationHandler<T> implements ValidationHandler<T> {

  private ValidationHandler<T> nextHandler;

  @Override
  public ValidationHandler<T> setNext(ValidationHandler<T> handler) {
    this.nextHandler = handler;
    return handler;
  }

  @Override
  public void validate(T request) {
    if (nextHandler != null) {
      nextHandler.validate(request);
    }
  }

  protected void proceedToNext(T request) {
    if (nextHandler != null) {
      nextHandler.validate(request);
    }
  }
}

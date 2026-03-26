package in.rsh.cab.chain;

import in.rsh.cab.model.BookingRequest;

public abstract class AbstractValidationHandler implements ValidationHandler {

  private ValidationHandler nextHandler;

  @Override
  public ValidationHandler setNext(ValidationHandler handler) {
    this.nextHandler = handler;
    return handler;
  }

  @Override
  public void validate(BookingRequest request) {
    if (nextHandler != null) {
      nextHandler.validate(request);
    }
  }

  protected void proceedToNext(BookingRequest request) {
    if (nextHandler != null) {
      nextHandler.validate(request);
    }
  }
}

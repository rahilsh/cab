package in.rsh.cab.chain;

import in.rsh.cab.model.BookingRequest;

public interface ValidationHandler {

  ValidationHandler setNext(ValidationHandler handler);

  void validate(BookingRequest request);
}

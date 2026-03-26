package in.rsh.cab.chain;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.model.BookingRequest;
import org.springframework.stereotype.Component;

@Component
public class EmployeeIdValidationHandler extends AbstractValidationHandler<BookingRequest> {

  @Override
  public void validate(BookingRequest request) {
    if (request.employeeId() == null || request.employeeId().isBlank()) {
      throw new InvalidRequestException("Employee ID is required");
    }
    super.proceedToNext(request);
  }
}

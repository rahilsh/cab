package in.rsh.cab.chain;

import in.rsh.cab.model.BookingRequest;
import org.springframework.stereotype.Component;

@Component
public class ValidationChain {

  private final ValidationHandler<BookingRequest> firstHandler;

  public ValidationChain(
      EmployeeIdValidationHandler employeeIdValidationHandler,
      CityValidationHandler cityValidationHandler) {
    employeeIdValidationHandler.setNext(cityValidationHandler);
    this.firstHandler = employeeIdValidationHandler;
  }

  public void validate(BookingRequest request) {
    firstHandler.validate(request);
  }
}

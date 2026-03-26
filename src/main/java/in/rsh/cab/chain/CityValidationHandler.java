package in.rsh.cab.chain;

import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.model.BookingRequest;
import in.rsh.cab.service.CityService;
import org.springframework.stereotype.Component;

@Component
public class CityValidationHandler extends AbstractValidationHandler<BookingRequest> {

  private final CityService cityService;

  public CityValidationHandler(CityService cityService) {
    this.cityService = cityService;
  }

  @Override
  public void validate(BookingRequest request) {
    if (request.fromCity() == null || request.toCity() == null) {
      throw new InvalidRequestException("From and To city are required");
    }
    cityService.validateCityOrThrow(request.fromCity());
    cityService.validateCityOrThrow(request.toCity());
    if (request.fromCity().equals(request.toCity())) {
      throw new InvalidRequestException("From and To city are same");
    }
    super.proceedToNext(request);
  }
}

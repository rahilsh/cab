package in.rsh.cab.template;

import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.exception.InvalidRequestException;
import in.rsh.cab.mapper.BookingMapper;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.Cab;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.service.CabService;
import in.rsh.cab.service.CityService;
import org.springframework.stereotype.Component;

@Component
public class StandardBookingTemplate extends BookingTemplate {

  private final CabService cabService;
  private final CityService cityService;
  private final BookingMapper bookingMapper;

  public StandardBookingTemplate(
      BookingJpaRepository bookingJpaRepository,
      CabService cabService,
      CityService cityService,
      BookingMapper bookingMapper) {
    super(bookingJpaRepository);
    this.cabService = cabService;
    this.cityService = cityService;
    this.bookingMapper = bookingMapper;
  }

  @Override
  protected void validateRequest(String employeeId, Integer fromCity, Integer toCity) {
  }

  @Override
  protected void validateCities(Integer fromCity, Integer toCity) {
    cityService.validateCityOrThrow(fromCity);
    cityService.validateCityOrThrow(toCity);
    if (fromCity.equals(toCity)) {
      throw new InvalidRequestException("From and To city are same");
    }
  }

  @Override
  protected Cab reserveCab(Integer fromCity, Integer toCity) {
    return cabService.reserveMostSuitableCab(fromCity, toCity);
  }

  @Override
  protected void postProcessing(BookingEntity entity) {
  }

  @Override
  protected Booking mapToModel(BookingEntity entity) {
    return bookingMapper.toModel(entity);
  }
}

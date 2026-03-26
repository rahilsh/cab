package in.rsh.cab.template;

import in.rsh.cab.chain.ValidationChain;
import in.rsh.cab.entity.BookingEntity;
import in.rsh.cab.mapper.BookingMapper;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.BookingRequest;
import in.rsh.cab.model.Cab;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.service.CabService;
import org.springframework.stereotype.Component;

@Component
public class StandardBookingTemplate extends BookingTemplate {

  private final CabService cabService;
  private final BookingMapper bookingMapper;
  private final ValidationChain validationChain;

  public StandardBookingTemplate(
      BookingJpaRepository bookingJpaRepository,
      CabService cabService,
      BookingMapper bookingMapper,
      ValidationChain validationChain) {
    super(bookingJpaRepository);
    this.cabService = cabService;
    this.bookingMapper = bookingMapper;
    this.validationChain = validationChain;
  }

  @Override
  protected void validateRequest(String employeeId, Integer fromCity, Integer toCity) {
    validationChain.validate(new BookingRequest(employeeId, fromCity, toCity, null));
  }

  @Override
  protected void validateCities(Integer fromCity, Integer toCity) {
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

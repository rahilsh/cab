package in.r.cab.admin.controller;

import com.google.gson.Gson;
import in.r.cab.admin.model.request.BookCabRequest;
import in.r.cab.admin.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

  @Autowired
  private BookingService bookingService;

  @RequestMapping(
      value = "bookings",
      method = RequestMethod.POST,
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String cabs(@RequestBody BookCabRequest request) {
    request.validate();
    return new Gson()
        .toJson(
            bookingService.bookCab(
                request.getEmployeeId(), request.getFromCity(), request.getToCity()));
  }

  @RequestMapping(
      value = "bookings",
      method = RequestMethod.GET,
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllBookings() {
    return new Gson().toJson(bookingService.getAllBookings());
  }
}

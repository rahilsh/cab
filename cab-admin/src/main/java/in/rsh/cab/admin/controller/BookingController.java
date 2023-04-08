package in.rsh.cab.admin.controller;

import com.google.gson.Gson;
import in.rsh.cab.admin.model.request.BookCabRequest;
import in.rsh.cab.admin.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

  private final BookingService bookingService;

  @Autowired
  public BookingController(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  @PostMapping(
      value = "bookings",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String bookingCab(@RequestBody BookCabRequest request) {
    request.validate();
    return new Gson()
        .toJson(
            bookingService.bookCab(
                request.employeeId(), request.fromCity(), request.toCity()));
  }

  @GetMapping(
      value = "bookings",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllBookings() {
    return new Gson().toJson(bookingService.getAllBookings());
  }
}

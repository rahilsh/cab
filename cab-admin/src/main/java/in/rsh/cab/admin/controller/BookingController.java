package in.rsh.cab.admin.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.rsh.cab.admin.model.request.BookCabRequest;
import in.rsh.cab.admin.service.BookingService;
import in.rsh.cab.commons.adapter.LocalDateTimeDeserializer;
import in.rsh.cab.commons.adapter.LocalDateTimeSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class BookingController {

  private final BookingService bookingService;

  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
          .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
          .create();

  @Autowired
  public BookingController(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  @PostMapping(
      value = "bookings",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String bookingCab(
      @RequestBody BookCabRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    request.validate();
    return gson.toJson(
        bookingService.bookCab(
            request.employeeId(), request.fromCity(), request.toCity(), idempotencyKey));
  }

  @GetMapping(
      value = "bookings",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllBookings() {
    return gson.toJson(bookingService.getAllBookings());
  }
}

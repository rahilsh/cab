package in.rsh.cab.controller;

import in.rsh.cab.model.request.BookCabRequest;
import in.rsh.cab.model.response.BookingResponse;
import in.rsh.cab.service.BookingService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
  public ResponseEntity<BookingResponse> bookingCab(
      @RequestBody BookCabRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    request.validate();
    BookingResponse response = bookingService.bookCabResponse(
        request.employeeId(), request.fromCity(), request.toCity(), idempotencyKey);
    return ResponseEntity.ok(response);
  }

  @GetMapping(
      value = "bookings",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<BookingResponse>> getAllBookings() {
    return ResponseEntity.ok(bookingService.getAllBookingsResponse());
  }
}

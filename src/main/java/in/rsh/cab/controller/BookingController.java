package in.rsh.cab.controller;

import in.rsh.cab.model.request.BookCabRequest;
import in.rsh.cab.model.response.BookingResponse;
import in.rsh.cab.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;

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
  public ResponseEntity<Page<BookingResponse>> getAllBookings(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safeSize = Math.min(size > 0 ? size : DEFAULT_SIZE, MAX_SIZE);
    int safePage = page >= 0 ? page : DEFAULT_PAGE;
    Pageable pageable = PageRequest.of(safePage, safeSize);
    return ResponseEntity.ok(bookingService.getAllBookings(pageable));
  }
}

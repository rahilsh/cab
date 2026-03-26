package in.rsh.cab.service;

import in.rsh.cab.entity.IdempotencyKeyEntity;
import in.rsh.cab.mapper.BookingMapper;
import in.rsh.cab.model.Booking;
import in.rsh.cab.model.response.BookingResponse;
import in.rsh.cab.repository.BookingJpaRepository;
import in.rsh.cab.repository.IdempotencyKeyJpaRepository;
import in.rsh.cab.template.BookingTemplate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

  private final BookingJpaRepository bookingJpaRepository;
  private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
  private final BookingMapper bookingMapper;
  private final BookingTemplate bookingTemplate;

  @Autowired
  public BookingService(
      BookingJpaRepository bookingJpaRepository,
      IdempotencyKeyJpaRepository idempotencyKeyJpaRepository,
      BookingMapper bookingMapper,
      BookingTemplate bookingTemplate) {
    this.bookingJpaRepository = bookingJpaRepository;
    this.idempotencyKeyJpaRepository = idempotencyKeyJpaRepository;
    this.bookingMapper = bookingMapper;
    this.bookingTemplate = bookingTemplate;
  }

  @Transactional
  public Booking bookCab(
      String employeeId, Integer fromCity, Integer toCity, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return bookingTemplate.execute(employeeId, fromCity, toCity);
    }

    Optional<IdempotencyKeyEntity> existingKey = idempotencyKeyJpaRepository.findById(idempotencyKey);
    if (existingKey.isPresent()) {
      return bookingJpaRepository.findById(existingKey.get().getBookingId()).map(bookingMapper::toModel).orElse(null);
    }

    Booking booking = bookingTemplate.execute(employeeId, fromCity, toCity);
    try {
      idempotencyKeyJpaRepository.save(new IdempotencyKeyEntity(idempotencyKey, booking.getBookingId()));
    } catch (Exception e) {
      try {
        bookingJpaRepository.deleteById(booking.getBookingId());
      } catch (Exception deleteEx) {
        throw new IllegalStateException("Failed to create booking and cleanup failed. Booking ID: " + booking.getBookingId(), deleteEx);
      }
      throw e;
    }
    return booking;
  }

  public Page<BookingResponse> getAllBookings(Pageable pageable) {
    return bookingJpaRepository.findAll(pageable)
        .map(bookingMapper::toModel)
        .map(bookingMapper::toResponse);
  }

  public BookingResponse bookCabResponse(
      String employeeId, Integer fromCity, Integer toCity, String idempotencyKey) {
    Booking booking = bookCab(employeeId, fromCity, toCity, idempotencyKey);
    return bookingMapper.toResponse(booking);
  }
}

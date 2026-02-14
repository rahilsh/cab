package in.rsh.cab.admin.store;

import in.rsh.cab.commons.model.Booking;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class IdempotencyStore {

  private final Map<String, BookingRecord> records = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  public Booking withIdempotency(
      String key, BookingRequest request, Supplier<Booking> bookingSupplier) {
    ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
    lock.lock();
    try {
      BookingRecord existing = records.get(key);
      if (existing != null) {
        if (!existing.request().equals(request)) {
          throw new IllegalArgumentException("Idempotency key already used for a different request");
        }
        return existing.booking();
      }
      Booking booking = bookingSupplier.get();
      records.put(key, new BookingRecord(booking, request));
      return booking;
    } finally {
      lock.unlock();
    }
  }

  public record BookingRequest(Integer employeeId, Integer fromCity, Integer toCity) {}

  public record BookingRecord(Booking booking, BookingRequest request) {}
}

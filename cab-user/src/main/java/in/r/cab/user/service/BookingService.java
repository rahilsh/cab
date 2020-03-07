package in.r.cab.user.service;

import in.r.cab.user.exception.CabNotAvailableException;
import in.r.cab.user.model.Booking;
import in.r.cab.user.model.Cab;
import in.r.cab.user.model.Location;
import in.r.cab.user.model.Rider;
import in.r.cab.user.store.GenericStore;
import in.r.cab.user.store.StoreFactory;
import in.r.cab.user.util.IDUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BookingService {

  public static final double MAX_DISTANCE_DRIVER_CAN_TRAVEL = 10.0;
  private final GenericStore<Booking> bookingStore;
  private final GenericStore<Cab> cabStore;

  public BookingService() {
    bookingStore = StoreFactory.getInstance().getStore(Booking.class);
    cabStore = StoreFactory.getInstance().getStore(Cab.class);
  }

  public Booking bookCab(Rider rider) {
    Optional<Cab> cabOptional =
        cabStore.getAll().stream()
            .filter(cab -> cab.getStatus().equals(Cab.CabStatus.AVAILABLE))
            .min((c1, c2) -> getNearestCab(c1.getLocation(), c2.getLocation()));
    if (cabOptional.isPresent()) {
      if (isDistanceMoreThanThreshold(
          rider.getCurrentLocation(), cabOptional.get().getLocation())) {
        throw new CabNotAvailableException();
      }
      Cab cab = cabOptional.get().toBuilder().status(Cab.CabStatus.ON_RIDE).build();
      cabStore.put(cab.getCabId(), cab);

      Booking booking =
          Booking.builder()
              .bookingId(IDUtil.generateID())
              .cabId(cab.getCabId())
              .riderId(rider.getPersonId())
              .startTime(LocalDateTime.now())
              .status(Booking.BookingStatus.IN_PROGRESS)
              .build();
      bookingStore.put(booking.getBookingId(), booking);
      return booking;
    }
    throw new CabNotAvailableException();
  }

  private boolean isDistanceMoreThanThreshold(Location currentLocation, Location location) {
    return (Math.sqrt(
            Math.pow(currentLocation.getX() - location.getX(), 2)
                + Math.pow(currentLocation.getY() - location.getY(), 2)))
        > MAX_DISTANCE_DRIVER_CAN_TRAVEL;
  }

  private int getNearestCab(Location location, Location location1) {
    return ((location.getX() + location.getY()) - (location1.getX() + location1.getY()));
  }

  public List<Booking> getBookingsForRider(Rider rider) {
    return bookingStore.getAll().stream()
        .filter(booking -> booking.getRiderId().equals(rider.getPersonId()))
        .collect(Collectors.toList());
  }
}

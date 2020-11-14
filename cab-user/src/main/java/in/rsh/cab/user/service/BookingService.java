package in.rsh.cab.user.service;

import in.rsh.cab.user.exception.CabNotAvailableException;
import in.rsh.cab.user.model.Booking;
import in.rsh.cab.user.model.Cab;
import in.rsh.cab.user.model.Location;
import in.rsh.cab.user.model.Rider;
import in.rsh.cab.user.store.GenericStore;
import in.rsh.cab.user.store.StoreFactory;
import in.rsh.cab.user.util.IDUtil;
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
            Math.pow(currentLocation.getLatitude() - location.getLatitude(), 2)
                + Math.pow(currentLocation.getLongitude() - location.getLongitude(), 2)))
        > MAX_DISTANCE_DRIVER_CAN_TRAVEL;
  }

  private int getNearestCab(Location location, Location location1) {
    return ((location.getLatitude() + location.getLongitude())
        - (location1.getLatitude() + location1.getLongitude()));
  }

  public List<Booking> getBookingsForRider(Rider rider) {
    return bookingStore.getAll().stream()
        .filter(booking -> booking.getRiderId().equals(rider.getPersonId()))
        .collect(Collectors.toList());
  }
}

package in.rsh.cab.strategy;

import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import java.util.Comparator;

public class DistanceBasedSelectionStrategy implements CabSelectionStrategy {

  public static final String NAME = "DISTANCE";

  @Override
  public Comparator<Cab> getComparator(Location pickupLocation) {
    return Comparator.comparingLong(
        cab -> {
          Location cabLocation = cab.getLocation();
          if (cabLocation == null || pickupLocation == null) {
            return Long.MAX_VALUE;
          }
          return calculateDistance(cabLocation, pickupLocation);
        });
  }

  @Override
  public String getName() {
    return NAME;
  }

  private long calculateDistance(Location from, Location to) {
    int latDiff = from.latitude() - to.latitude();
    int lonDiff = from.longitude() - to.longitude();
    return (long) Math.sqrt((long) latDiff * latDiff + (long) lonDiff * lonDiff);
  }
}

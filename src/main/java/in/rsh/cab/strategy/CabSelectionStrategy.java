package in.rsh.cab.strategy;

import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import java.util.Comparator;

public interface CabSelectionStrategy {

  Comparator<Cab> getComparator(Location pickupLocation);

  String getName();
}

package in.rsh.cab.commons.strategy;

import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Location;

import java.util.Comparator;
import java.util.List;

public interface CabSelectionStrategy {

  Comparator<Cab> getComparator(Location pickupLocation);

  String getName();
}

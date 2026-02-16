package in.rsh.cab.commons.strategy;

import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Location;

import java.util.Comparator;

public class IdleTimeSelectionStrategy implements CabSelectionStrategy {

  public static final String NAME = "IDLE_TIME";

  @Override
  public Comparator<Cab> getComparator(Location pickupLocation) {
    return Comparator.comparingLong(
        cab -> cab.getIdleFrom() == null ? Long.MAX_VALUE : cab.getIdleFrom());
  }

  @Override
  public String getName() {
    return NAME;
  }
}

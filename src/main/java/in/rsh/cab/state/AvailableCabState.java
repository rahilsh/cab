package in.rsh.cab.state;

import in.rsh.cab.model.Cab;

public class AvailableCabState implements CabState {

  private static final AvailableCabState INSTANCE = new AvailableCabState();

  private AvailableCabState() {
  }

  public static AvailableCabState getInstance() {
    return INSTANCE;
  }

  @Override
  public CabStatus getStatus() {
    return CabStatus.AVAILABLE;
  }

  @Override
  public CabState makeAvailable(Cab cab) {
    return this;
  }

  @Override
  public CabState makeUnavailable(Cab cab) {
    cab.setStatus(Cab.CabStatus.UNAVAILABLE);
    return UnavailableCabState.getInstance();
  }

  @Override
  public CabState startRide(Cab cab) {
    cab.setStatus(Cab.CabStatus.ON_RIDE);
    return OnRideCabState.getInstance();
  }

  @Override
  public CabState endRide(Cab cab) {
    throw new IllegalStateException("Cannot end ride - cab is not on a ride");
  }
}

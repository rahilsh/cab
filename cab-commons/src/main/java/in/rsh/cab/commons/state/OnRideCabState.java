package in.rsh.cab.commons.state;

import in.rsh.cab.commons.model.Cab;

public class OnRideCabState implements CabState {

  private static final OnRideCabState INSTANCE = new OnRideCabState();

  private OnRideCabState() {}

  public static OnRideCabState getInstance() {
    return INSTANCE;
  }

  @Override
  public CabStatus getStatus() {
    return CabStatus.ON_RIDE;
  }

  @Override
  public CabState makeAvailable(Cab cab) {
    throw new IllegalStateException("Cannot make cab available while on ride");
  }

  @Override
  public CabState makeUnavailable(Cab cab) {
    throw new IllegalStateException("Cannot make cab unavailable while on ride");
  }

  @Override
  public CabState startRide(Cab cab) {
    return this;
  }

  @Override
  public CabState endRide(Cab cab) {
    cab.setStatus(Cab.CabStatus.AVAILABLE);
    cab.setIdleFrom(System.currentTimeMillis());
    return AvailableCabState.getInstance();
  }
}

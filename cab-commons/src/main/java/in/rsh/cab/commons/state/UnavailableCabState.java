package in.rsh.cab.commons.state;

import in.rsh.cab.commons.model.Cab;

public class UnavailableCabState implements CabState {

  private static final UnavailableCabState INSTANCE = new UnavailableCabState();

  private UnavailableCabState() {}

  public static UnavailableCabState getInstance() {
    return INSTANCE;
  }

  @Override
  public CabStatus getStatus() {
    return CabStatus.UNAVAILABLE;
  }

  @Override
  public CabState makeAvailable(Cab cab) {
    cab.setStatus(Cab.CabStatus.AVAILABLE);
    cab.setIdleFrom(System.currentTimeMillis());
    return AvailableCabState.getInstance();
  }

  @Override
  public CabState makeUnavailable(Cab cab) {
    return this;
  }

  @Override
  public CabState startRide(Cab cab) {
    throw new IllegalStateException("Cannot start ride while cab is unavailable");
  }

  @Override
  public CabState endRide(Cab cab) {
    throw new IllegalStateException("Cannot end ride while cab is unavailable");
  }
}

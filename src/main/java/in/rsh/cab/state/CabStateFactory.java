package in.rsh.cab.state;

import in.rsh.cab.model.Cab;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CabStateFactory {

  private static final Map<Cab.CabStatus, CabState> STATES = new ConcurrentHashMap<>();

  static {
    STATES.put(Cab.CabStatus.AVAILABLE, AvailableCabState.getInstance());
    STATES.put(Cab.CabStatus.UNAVAILABLE, UnavailableCabState.getInstance());
    STATES.put(Cab.CabStatus.ON_RIDE, OnRideCabState.getInstance());
  }

  private CabStateFactory() {
  }

  public static CabState getState(Cab.CabStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("Unknown cab status: null");
    }
    CabState state = STATES.get(status);
    if (state == null) {
      throw new IllegalArgumentException("Unknown cab status: " + status);
    }
    return state;
  }
}

package in.rsh.cab.state;

import in.rsh.cab.model.Cab;

public interface CabState {

  CabStatus getStatus();

  CabState makeAvailable(Cab cab);

  CabState makeUnavailable(Cab cab);

  CabState startRide(Cab cab);

  CabState endRide(Cab cab);

  enum CabStatus {
    AVAILABLE,
    UNAVAILABLE,
    ON_RIDE
  }
}

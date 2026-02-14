package in.rsh.cab.commons.state;

import in.rsh.cab.commons.model.Cab;

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

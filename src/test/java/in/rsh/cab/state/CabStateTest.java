package in.rsh.cab.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Cab.CabStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CabStateFactoryTest {

  @Nested
  class GetStateTests {

    @Test
    void getState_withAvailableStatus_shouldReturnAvailableState() {
      CabState state = CabStateFactory.getState(CabStatus.AVAILABLE);
      assertNotNull(state);
      assertEquals(CabState.CabStatus.AVAILABLE, state.getStatus());
    }

    @Test
    void getState_withUnavailableStatus_shouldReturnUnavailableState() {
      CabState state = CabStateFactory.getState(CabStatus.UNAVAILABLE);
      assertNotNull(state);
      assertEquals(CabState.CabStatus.UNAVAILABLE, state.getStatus());
    }

    @Test
    void getState_withOnRideStatus_shouldReturnOnRideState() {
      CabState state = CabStateFactory.getState(CabStatus.ON_RIDE);
      assertNotNull(state);
      assertEquals(CabState.CabStatus.ON_RIDE, state.getStatus());
    }

    @Test
    void getState_withNullStatus_shouldThrowException() {
      assertThrows(IllegalArgumentException.class, () -> CabStateFactory.getState(null));
    }
  }
}

class CabStateTest {

  @Nested
  class AvailableCabStateTests {

    @Test
    void getStatus_shouldReturnAvailable() {
      CabState state = AvailableCabState.getInstance();
      assertEquals(CabState.CabStatus.AVAILABLE, state.getStatus());
    }

    @Test
    void makeAvailable_shouldReturnSameState() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = AvailableCabState.getInstance();
      CabState result = state.makeAvailable(cab);
      assertEquals(AvailableCabState.getInstance(), result);
    }

    @Test
    void makeUnavailable_shouldReturnUnavailableState() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = AvailableCabState.getInstance();
      CabState result = state.makeUnavailable(cab);
      assertEquals(CabState.CabStatus.UNAVAILABLE, result.getStatus());
    }

    @Test
    void startRide_shouldReturnOnRideState() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = AvailableCabState.getInstance();
      CabState result = state.startRide(cab);
      assertEquals(CabState.CabStatus.ON_RIDE, result.getStatus());
    }

    @Test
    void endRide_shouldThrowException() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = AvailableCabState.getInstance();
      assertThrows(IllegalStateException.class, () -> state.endRide(cab));
    }
  }

  @Nested
  class UnavailableCabStateTests {

    @Test
    void getStatus_shouldReturnUnavailable() {
      CabState state = UnavailableCabState.getInstance();
      assertEquals(CabState.CabStatus.UNAVAILABLE, state.getStatus());
    }

    @Test
    void makeUnavailable_shouldReturnSameState() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = UnavailableCabState.getInstance();
      CabState result = state.makeUnavailable(cab);
      assertEquals(UnavailableCabState.getInstance(), result);
    }

    @Test
    void makeAvailable_shouldReturnAvailableState() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = UnavailableCabState.getInstance();
      CabState result = state.makeAvailable(cab);
      assertEquals(CabState.CabStatus.AVAILABLE, result.getStatus());
      assertNotNull(cab.getIdleFrom());
    }

    @Test
    void startRide_shouldThrowException() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = UnavailableCabState.getInstance();
      assertThrows(IllegalStateException.class, () -> state.startRide(cab));
    }

    @Test
    void endRide_shouldThrowException() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = UnavailableCabState.getInstance();
      assertThrows(IllegalStateException.class, () -> state.endRide(cab));
    }
  }

  @Nested
  class OnRideCabStateTests {

    @Test
    void getStatus_shouldReturnOnRide() {
      CabState state = OnRideCabState.getInstance();
      assertEquals(CabState.CabStatus.ON_RIDE, state.getStatus());
    }

    @Test
    void makeAvailable_shouldThrowException() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = OnRideCabState.getInstance();
      assertThrows(IllegalStateException.class, () -> state.makeAvailable(cab));
    }

    @Test
    void makeUnavailable_shouldThrowException() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = OnRideCabState.getInstance();
      assertThrows(IllegalStateException.class, () -> state.makeUnavailable(cab));
    }

    @Test
    void startRide_shouldReturnSameState() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = OnRideCabState.getInstance();
      CabState result = state.startRide(cab);
      assertEquals(OnRideCabState.getInstance(), result);
    }

    @Test
    void endRide_shouldReturnAvailableState() {
      Cab cab = Cab.builder().cabId(1).build();
      CabState state = OnRideCabState.getInstance();
      CabState result = state.endRide(cab);
      assertEquals(CabState.CabStatus.AVAILABLE, result.getStatus());
      assertNotNull(cab.getIdleFrom());
    }
  }
}

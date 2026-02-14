package in.rsh.cab.commons.state;

import in.rsh.cab.commons.model.Cab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CabStateTest {

  private Cab cab;

  @BeforeEach
  void setUp() {
    cab = Cab.builder()
        .cabId("1")
        .driverId("100")
        .model("Toyota Innova")
        .cityId(1)
        .status(Cab.CabStatus.AVAILABLE)
        .idleFrom(System.currentTimeMillis())
        .build();
  }

  @Nested
  class AvailableCabStateTests {

    @Test
    void getStatus_shouldReturnAvailable() {
      CabState state = AvailableCabState.getInstance();
      assertEquals(CabState.CabStatus.AVAILABLE, state.getStatus());
    }

    @Test
    void makeAvailable_onAvailableCab_shouldReturnSameState() {
      CabState state = AvailableCabState.getInstance();
      CabState newState = state.makeAvailable(cab);
      assertSame(AvailableCabState.getInstance(), newState);
      assertEquals(Cab.CabStatus.AVAILABLE, cab.getStatus());
    }

    @Test
    void makeUnavailable_onAvailableCab_shouldTransitionToUnavailable() {
      CabState state = AvailableCabState.getInstance();
      CabState newState = state.makeUnavailable(cab);
      assertSame(UnavailableCabState.getInstance(), newState);
      assertEquals(Cab.CabStatus.UNAVAILABLE, cab.getStatus());
    }

    @Test
    void startRide_onAvailableCab_shouldTransitionToOnRide() {
      CabState state = AvailableCabState.getInstance();
      CabState newState = state.startRide(cab);
      assertSame(OnRideCabState.getInstance(), newState);
      assertEquals(Cab.CabStatus.ON_RIDE, cab.getStatus());
    }

    @Test
    void endRide_onAvailableCab_shouldThrowException() {
      CabState state = AvailableCabState.getInstance();
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> state.endRide(cab)
      );
      assertEquals("Cannot end ride - cab is not on a ride", exception.getMessage());
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
    void makeAvailable_onUnavailableCab_shouldTransitionToAvailable() {
      cab.setStatus(Cab.CabStatus.UNAVAILABLE);
      CabState state = UnavailableCabState.getInstance();
      CabState newState = state.makeAvailable(cab);
      assertSame(AvailableCabState.getInstance(), newState);
      assertEquals(Cab.CabStatus.AVAILABLE, cab.getStatus());
      assertNotNull(cab.getIdleFrom());
    }

    @Test
    void makeUnavailable_onUnavailableCab_shouldReturnSameState() {
      cab.setStatus(Cab.CabStatus.UNAVAILABLE);
      CabState state = UnavailableCabState.getInstance();
      CabState newState = state.makeUnavailable(cab);
      assertSame(UnavailableCabState.getInstance(), newState);
    }

    @Test
    void startRide_onUnavailableCab_shouldThrowException() {
      cab.setStatus(Cab.CabStatus.UNAVAILABLE);
      CabState state = UnavailableCabState.getInstance();
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> state.startRide(cab)
      );
      assertEquals("Cannot start ride while cab is unavailable", exception.getMessage());
    }

    @Test
    void endRide_onUnavailableCab_shouldThrowException() {
      cab.setStatus(Cab.CabStatus.UNAVAILABLE);
      CabState state = UnavailableCabState.getInstance();
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> state.endRide(cab)
      );
      assertEquals("Cannot end ride while cab is unavailable", exception.getMessage());
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
    void makeAvailable_onOnRideCab_shouldThrowException() {
      cab.setStatus(Cab.CabStatus.ON_RIDE);
      CabState state = OnRideCabState.getInstance();
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> state.makeAvailable(cab)
      );
      assertEquals("Cannot make cab available while on ride", exception.getMessage());
    }

    @Test
    void makeUnavailable_onOnRideCab_shouldThrowException() {
      cab.setStatus(Cab.CabStatus.ON_RIDE);
      CabState state = OnRideCabState.getInstance();
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> state.makeUnavailable(cab)
      );
      assertEquals("Cannot make cab unavailable while on ride", exception.getMessage());
    }

    @Test
    void startRide_onOnRideCab_shouldReturnSameState() {
      cab.setStatus(Cab.CabStatus.ON_RIDE);
      CabState state = OnRideCabState.getInstance();
      CabState newState = state.startRide(cab);
      assertSame(OnRideCabState.getInstance(), newState);
    }

    @Test
    void endRide_onOnRideCab_shouldTransitionToAvailable() {
      cab.setStatus(Cab.CabStatus.ON_RIDE);
      CabState state = OnRideCabState.getInstance();
      CabState newState = state.endRide(cab);
      assertSame(AvailableCabState.getInstance(), newState);
      assertEquals(Cab.CabStatus.AVAILABLE, cab.getStatus());
      assertNotNull(cab.getIdleFrom());
    }
  }

  @Nested
  class CabStateFactoryTests {

    @Test
    void getState_withAvailableStatus_shouldReturnAvailableState() {
      CabState state = CabStateFactory.getState(Cab.CabStatus.AVAILABLE);
      assertEquals(CabState.CabStatus.AVAILABLE, state.getStatus());
    }

    @Test
    void getState_withUnavailableStatus_shouldReturnUnavailableState() {
      CabState state = CabStateFactory.getState(Cab.CabStatus.UNAVAILABLE);
      assertEquals(CabState.CabStatus.UNAVAILABLE, state.getStatus());
    }

    @Test
    void getState_withOnRideStatus_shouldReturnOnRideState() {
      CabState state = CabStateFactory.getState(Cab.CabStatus.ON_RIDE);
      assertEquals(CabState.CabStatus.ON_RIDE, state.getStatus());
    }

    @Test
    void getState_withUnknownStatus_shouldThrowException() {
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> CabStateFactory.getState(null)
      );
      assertEquals("Unknown cab status: null", exception.getMessage());
    }
  }

  @Nested
  class FullStateTransitionFlowTests {

    @Test
    void completeRideFlow_shouldWorkCorrectly() {
      CabState state = CabStateFactory.getState(cab.getStatus());
      assertEquals(CabState.CabStatus.AVAILABLE, state.getStatus());

      state = state.startRide(cab);
      assertEquals(CabState.CabStatus.ON_RIDE, state.getStatus());
      assertEquals(Cab.CabStatus.ON_RIDE, cab.getStatus());

      state = state.endRide(cab);
      assertEquals(CabState.CabStatus.AVAILABLE, state.getStatus());
      assertEquals(Cab.CabStatus.AVAILABLE, cab.getStatus());
    }

    @Test
    void makeUnavailableThenAvailable_shouldWorkCorrectly() {
      CabState state = CabStateFactory.getState(cab.getStatus());

      state = state.makeUnavailable(cab);
      assertEquals(CabState.CabStatus.UNAVAILABLE, state.getStatus());
      assertEquals(Cab.CabStatus.UNAVAILABLE, cab.getStatus());

      state = state.makeAvailable(cab);
      assertEquals(CabState.CabStatus.AVAILABLE, state.getStatus());
      assertEquals(Cab.CabStatus.AVAILABLE, cab.getStatus());
    }

    @Test
    void invalidTransitionFromOnRide_toUnavailable_shouldThrowException() {
      cab.setStatus(Cab.CabStatus.ON_RIDE);
      CabState state = CabStateFactory.getState(Cab.CabStatus.ON_RIDE);

      assertThrows(IllegalStateException.class, () -> state.makeUnavailable(cab));
    }

    @Test
    void invalidTransitionFromOnRide_toAvailable_shouldThrowException() {
      cab.setStatus(Cab.CabStatus.ON_RIDE);
      CabState state = CabStateFactory.getState(Cab.CabStatus.ON_RIDE);

      assertThrows(IllegalStateException.class, () -> state.makeAvailable(cab));
    }

    @Test
    void invalidTransitionFromUnavailable_toOnRide_shouldThrowException() {
      cab.setStatus(Cab.CabStatus.UNAVAILABLE);
      CabState state = CabStateFactory.getState(Cab.CabStatus.UNAVAILABLE);

      assertThrows(IllegalStateException.class, () -> state.startRide(cab));
    }
  }

  @Nested
  class SingletonPatternTests {

    @Test
    void availableState_shouldBeSingleton() {
      assertSame(AvailableCabState.getInstance(), AvailableCabState.getInstance());
    }

    @Test
    void unavailableState_shouldBeSingleton() {
      assertSame(UnavailableCabState.getInstance(), UnavailableCabState.getInstance());
    }

    @Test
    void onRideState_shouldBeSingleton() {
      assertSame(OnRideCabState.getInstance(), OnRideCabState.getInstance());
    }

    @Test
    void factory_shouldReturnSingletonStates() {
      CabState state1 = CabStateFactory.getState(Cab.CabStatus.AVAILABLE);
      CabState state2 = CabStateFactory.getState(Cab.CabStatus.AVAILABLE);
      assertSame(state1, state2);
    }
  }
}

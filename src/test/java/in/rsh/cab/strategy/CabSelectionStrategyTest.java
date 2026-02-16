package in.rsh.cab.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import java.util.Comparator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CabSelectionStrategyTest {

  @Nested
  class DistanceBasedSelectionStrategyTests {

    @Test
    void getName_shouldReturnDistance() {
      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy();
      assertEquals("DISTANCE", strategy.getName());
    }

    @Test
    void getComparator_shouldSortByDistance() {
      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy();
      Location pickup = new Location(10, 10);

      Cab cab1 = Cab.builder().cabId(1).location(new Location(10, 11)).build();
      Cab cab2 = Cab.builder().cabId(2).location(new Location(10, 15)).build();
      Cab cab3 = Cab.builder().cabId(3).location(new Location(10, 12)).build();

      Comparator<Cab> comparator = strategy.getComparator(pickup);

      assertEquals(-1, comparator.compare(cab1, cab2));
      assertEquals(-1, comparator.compare(cab1, cab3));
      assertEquals(1, comparator.compare(cab2, cab3));
    }

    @Test
    void getComparator_withNullCabLocation_shouldReturnMaxValue() {
      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy();
      Location pickup = new Location(10, 10);

      Cab cab1 = Cab.builder().cabId(1).location(null).build();
      Cab cab2 = Cab.builder().cabId(2).location(new Location(10, 11)).build();

      Comparator<Cab> comparator = strategy.getComparator(pickup);

      assertEquals(1, comparator.compare(cab1, cab2));
    }

    @Test
    void getComparator_withNullPickupLocation_shouldReturnMaxValue() {
      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy();

      Cab cab1 = Cab.builder().cabId(1).location(null).build();
      Cab cab2 = Cab.builder().cabId(2).location(null).build();

      Comparator<Cab> comparator = strategy.getComparator(null);

      assertEquals(0, comparator.compare(cab1, cab2));
    }
  }

  @Nested
  class IdleTimeSelectionStrategyTests {

    @Test
    void getName_shouldReturnIdleTime() {
      IdleTimeSelectionStrategy strategy = new IdleTimeSelectionStrategy();
      assertEquals("IDLE_TIME", strategy.getName());
    }

    @Test
    void getComparator_shouldSortByIdleTime() {
      IdleTimeSelectionStrategy strategy = new IdleTimeSelectionStrategy();
      Location pickup = new Location(10, 10);

      Cab cab1 = Cab.builder().cabId(1).idleFrom(1000L).build();
      Cab cab2 = Cab.builder().cabId(2).idleFrom(2000L).build();
      Cab cab3 = Cab.builder().cabId(3).idleFrom(1500L).build();

      Comparator<Cab> comparator = strategy.getComparator(pickup);

      assertEquals(-1, comparator.compare(cab1, cab2));
      assertEquals(-1, comparator.compare(cab1, cab3));
      assertEquals(1, comparator.compare(cab2, cab3));
    }

    @Test
    void getComparator_withNullIdleFrom_shouldReturnMaxValue() {
      IdleTimeSelectionStrategy strategy = new IdleTimeSelectionStrategy();
      Location pickup = new Location(10, 10);

      Cab cab1 = Cab.builder().cabId(1).idleFrom(null).build();
      Cab cab2 = Cab.builder().cabId(2).idleFrom(1000L).build();

      Comparator<Cab> comparator = strategy.getComparator(pickup);

      assertEquals(1, comparator.compare(cab1, cab2));
    }
  }
}

package in.rsh.cab.commons.strategy;

import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Location;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CabSelectionStrategyTest {

  @Nested
  class IdleTimeSelectionStrategyTests {

    @Test
    void getName_shouldReturnIdleTime() {
      CabSelectionStrategy strategy = new IdleTimeSelectionStrategy();
      assertEquals("IDLE_TIME", strategy.getName());
    }

    @Test
    void getComparator_shouldSortByIdleTime() {
      CabSelectionStrategy strategy = new IdleTimeSelectionStrategy();
      Comparator<Cab> comparator = strategy.getComparator(null);

      Cab cab1 = Cab.builder().idleFrom(1000L).build();
      Cab cab2 = Cab.builder().idleFrom(2000L).build();
      Cab cab3 = Cab.builder().idleFrom(500L).build();

      List<Cab> cabs = List.of(cab1, cab2, cab3);
      List<Cab> sorted = cabs.stream().sorted(comparator).toList();

      assertEquals(500L, sorted.get(0).getIdleFrom());
      assertEquals(1000L, sorted.get(1).getIdleFrom());
      assertEquals(2000L, sorted.get(2).getIdleFrom());
    }

    @Test
    void getComparator_withNullIdleFrom_shouldSortToEnd() {
      CabSelectionStrategy strategy = new IdleTimeSelectionStrategy();
      Comparator<Cab> comparator = strategy.getComparator(null);

      Cab cab1 = Cab.builder().idleFrom(1000L).build();
      Cab cab2 = Cab.builder().idleFrom(null).build();

      List<Cab> cabs = List.of(cab1, cab2);
      List<Cab> sorted = cabs.stream().sorted(comparator).toList();

      assertEquals(1000L, sorted.get(0).getIdleFrom());
      assertNull(sorted.get(1).getIdleFrom());
    }
  }

  @Nested
  class DistanceBasedSelectionStrategyTests {

    @Test
    void getName_shouldReturnDistance() {
      CabSelectionStrategy strategy = new DistanceBasedSelectionStrategy();
      assertEquals("DISTANCE", strategy.getName());
    }

    @Test
    void getComparator_shouldSortByDistance() {
      CabSelectionStrategy strategy = new DistanceBasedSelectionStrategy();
      Location pickupLocation = new Location(10, 10);

      Comparator<Cab> comparator = strategy.getComparator(pickupLocation);

      Cab cab1 = Cab.builder().location(new Location(10, 10)).build();
      Cab cab2 = Cab.builder().location(new Location(20, 20)).build();
      Cab cab3 = Cab.builder().location(new Location(15, 15)).build();

      List<Cab> cabs = List.of(cab1, cab2, cab3);
      List<Cab> sorted = cabs.stream().sorted(comparator).toList();

      assertEquals(10, sorted.get(0).getLocation().latitude());
      assertEquals(15, sorted.get(1).getLocation().latitude());
      assertEquals(20, sorted.get(2).getLocation().latitude());
    }

    @Test
    void getComparator_withNullCabLocation_shouldSortToEnd() {
      CabSelectionStrategy strategy = new DistanceBasedSelectionStrategy();
      Location pickupLocation = new Location(10, 10);

      Comparator<Cab> comparator = strategy.getComparator(pickupLocation);

      Cab cab1 = Cab.builder().location(new Location(10, 10)).build();
      Cab cab2 = Cab.builder().location(null).build();

      List<Cab> cabs = List.of(cab1, cab2);
      List<Cab> sorted = cabs.stream().sorted(comparator).toList();

      assertEquals(10, sorted.get(0).getLocation().latitude());
      assertNull(sorted.get(1).getLocation());
    }

    @Test
    void getComparator_withNullPickupLocation_shouldSortToEnd() {
      CabSelectionStrategy strategy = new DistanceBasedSelectionStrategy();

      Comparator<Cab> comparator = strategy.getComparator(null);

      Cab cab1 = Cab.builder().location(new Location(10, 10)).build();
      Cab cab2 = Cab.builder().location(new Location(20, 20)).build();

      List<Cab> cabs = List.of(cab1, cab2);
      List<Cab> sorted = cabs.stream().sorted(comparator).toList();

      assertEquals(10, sorted.get(0).getLocation().latitude());
      assertEquals(20, sorted.get(1).getLocation().latitude());
    }
  }
}

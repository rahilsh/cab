package in.rsh.cab.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.service.RedisGeoService;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CabSelectionStrategyTest {

  @Mock
  private RedisGeoService redisGeoService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  class DistanceBasedSelectionStrategyTests {

    @Test
    void getName_shouldReturnDistance() {
      when(redisGeoService.getDistanceInMeters(anyInt(), any(Location.class)))
          .thenReturn(1000L);
      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy(redisGeoService);
      assertEquals("DISTANCE", strategy.getName());
    }

    @Test
    void getComparator_shouldSortByDistance() {
      when(redisGeoService.getDistanceInMeters(1, new Location(10, 10))).thenReturn(111000L);
      when(redisGeoService.getDistanceInMeters(2, new Location(10, 10))).thenReturn(555000L);
      when(redisGeoService.getDistanceInMeters(3, new Location(10, 10))).thenReturn(222000L);

      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy(redisGeoService);
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
      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy(redisGeoService);
      Location pickup = new Location(10, 10);

      Cab cab1 = Cab.builder().cabId(1).location(null).build();
      Cab cab2 = Cab.builder().cabId(2).location(new Location(10, 11)).build();

      Comparator<Cab> comparator = strategy.getComparator(pickup);

      assertEquals(1, comparator.compare(cab1, cab2));
    }

    @Test
    void getComparator_withNullPickupLocation_shouldReturnMaxValue() {
      DistanceBasedSelectionStrategy strategy = new DistanceBasedSelectionStrategy(redisGeoService);

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

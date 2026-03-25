package in.rsh.cab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.model.Location;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class RedisGeoServiceTest {

  private RedisTemplate<String, String> redisTemplate;
  private GeoOperations<String, String> geoOperations;
  private ZSetOperations<String, String> zSetOperations;
  private RedisGeoService redisGeoService;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(RedisTemplate.class);
    geoOperations = mock(GeoOperations.class);
    zSetOperations = mock(ZSetOperations.class);
    when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    redisGeoService = new RedisGeoService(redisTemplate);
  }

  @Nested
  class UpdateCabLocationTests {

    @Test
    void updateCabLocation_shouldAddLocationToRedis() {
      Location location = new Location(12, 77);
      redisGeoService.updateCabLocation(1, location);

      verify(geoOperations).add(anyString(), any(Point.class), anyString());
    }

    @Test
    void updateCabLocation_withNullLocation_shouldNotCallRedis() {
      redisGeoService.updateCabLocation(1, null);
    }

    @Test
    void updateCabLocation_withNullCabId_shouldNotCallRedis() {
      Location location = new Location(12, 77);
      redisGeoService.updateCabLocation(null, location);
    }
  }

  @Nested
  class RemoveCabLocationTests {

    @Test
    void removeCabLocation_shouldRemoveFromRedis() {
      redisGeoService.removeCabLocation(1);

      verify(zSetOperations).remove("cabs:geo", "1");
    }

    @Test
    void removeCabLocation_withNullCabId_shouldNotCallRedis() {
      redisGeoService.removeCabLocation(null);
    }
  }

  @Nested
  class FindNearbyCabIdsTests {

    @Test
    void findNearbyCabIds_shouldReturnCabIds() {
      Location location = new Location(12, 77);
      @SuppressWarnings("unchecked")
      GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = mock(GeoResults.class);
      @SuppressWarnings("unchecked")
      GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult = mock(GeoResult.class);
      @SuppressWarnings("unchecked")
      RedisGeoCommands.GeoLocation<String> geoLocation = mock(RedisGeoCommands.GeoLocation.class);

      when(geoLocation.getName()).thenReturn("1");
      when(geoResult.getContent()).thenReturn(geoLocation);
      when(geoResults.getContent()).thenReturn(List.of(geoResult));
      when(geoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
          .thenReturn(geoResults);

      List<Integer> result = redisGeoService.findNearbyCabIds(location, 5.0);

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(1, result.get(0));
    }

    @Test
    void findNearbyCabIds_withNullLocation_shouldReturnEmptyList() {
      List<Integer> result = redisGeoService.findNearbyCabIds(null, 5.0);

      assertNotNull(result);
      assertEquals(0, result.size());
    }
  }

  @Nested
  class GetDistanceInMetersTests {

    @Test
    void getDistanceInMeters_shouldReturnDistance() {
      Location toLocation = new Location(12, 77);
      List<Point> points = List.of(new Point(77, 12));
      when(geoOperations.position("cabs:geo", "1")).thenReturn(points);

      Long result = redisGeoService.getDistanceInMeters(1, toLocation);

      assertNotNull(result);
      assertEquals(0, result);
    }

    @Test
    void getDistanceInMeters_withNullCabId_shouldReturnMaxValue() {
      Location toLocation = new Location(12, 77);

      Long result = redisGeoService.getDistanceInMeters(null, toLocation);

      assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void getDistanceInMeters_withNullLocation_shouldReturnMaxValue() {
      Long result = redisGeoService.getDistanceInMeters(1, null);

      assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void getDistanceInMeters_withNullPosition_shouldReturnMaxValue() {
      Location toLocation = new Location(12, 77);
      when(geoOperations.position("cabs:geo", "1")).thenReturn(null);

      Long result = redisGeoService.getDistanceInMeters(1, toLocation);

      assertEquals(Long.MAX_VALUE, result);
    }
  }

  @Nested
  class ClearAllTests {

    @Test
    void clearAll_shouldDeleteGeoKey() {
      redisGeoService.clearAll();

      verify(redisTemplate).delete("cabs:geo");
    }
  }
}

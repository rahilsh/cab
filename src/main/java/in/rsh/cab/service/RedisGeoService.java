package in.rsh.cab.service;

import in.rsh.cab.model.Location;
import java.util.List;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisGeoService {

  private static final String CAB_GEO_KEY = "cabs:geo";

  private final RedisTemplate<String, String> redisTemplate;
  private final GeoOperations<String, String> geoOperations;

  public RedisGeoService(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.geoOperations = redisTemplate.opsForGeo();
  }

  public void updateCabLocation(Integer cabId, Location location) {
    if (location == null || cabId == null) {
      return;
    }
    geoOperations.add(CAB_GEO_KEY, new Point(location.longitude(), location.latitude()), cabId.toString());
  }

  public void removeCabLocation(Integer cabId) {
    if (cabId == null) {
      return;
    }
    redisTemplate.opsForZSet().remove(CAB_GEO_KEY, cabId.toString());
  }

  public List<Integer> findNearbyCabIds(Location location, double radiusInKm) {
    if (location == null) {
      return List.of();
    }
    RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
        .newGeoRadiusArgs()
        .includeDistance()
        .sortAscending()
        .limit(100);

    var results = geoOperations.radius(
        CAB_GEO_KEY,
        new org.springframework.data.geo.Circle(
            new Point(location.longitude(), location.latitude()),
            new Distance(radiusInKm, Metrics.KILOMETERS)),
        args);

    return results.getContent().stream()
        .map(geoResult -> Integer.parseInt(geoResult.getContent().getName()))
        .toList();
  }

  public Long getDistanceInMeters(Integer cabId, Location toLocation) {
    if (cabId == null || toLocation == null) {
      return Long.MAX_VALUE;
    }
    try {
      var fromPos = geoOperations.position(CAB_GEO_KEY, cabId.toString());
      if (fromPos == null || fromPos.isEmpty()) {
        return Long.MAX_VALUE;
      }
      Point fromPoint = fromPos.get(0);
      if (fromPoint == null) {
        return Long.MAX_VALUE;
      }

      GeodesicData geodesic = Geodesic.WGS84.Inverse(
          fromPoint.getY(), fromPoint.getX(),
          toLocation.latitude(), toLocation.longitude());
      return (long) geodesic.s12;
    } catch (Exception e) {
      return Long.MAX_VALUE;
    }
  }

  public void clearAll() {
    redisTemplate.delete(CAB_GEO_KEY);
  }
}

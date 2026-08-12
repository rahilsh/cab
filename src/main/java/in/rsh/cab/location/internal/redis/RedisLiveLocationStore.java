package in.rsh.cab.location.internal.redis;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.location.DriverLocation;
import in.rsh.cab.location.LiveLocationStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisLiveLocationStore implements LiveLocationStore {

  private static final DefaultRedisScript<Long> UPDATE = new DefaultRedisScript<>("""
      local current = redis.call('HGET', KEYS[2], ARGV[1])
      if current then
        local separator = string.find(current, ':')
        local sequence = tonumber(string.sub(current, 1, separator - 1))
        local recorded = tonumber(string.sub(current, separator + 1))
        if tonumber(ARGV[4]) <= sequence or tonumber(ARGV[5]) <= recorded then return 0 end
      end
      redis.call('GEOADD', KEYS[1], ARGV[2], ARGV[3], ARGV[1])
      redis.call('HSET', KEYS[2], ARGV[1], ARGV[4] .. ':' .. ARGV[5])
      return 1
      """, Long.class);

  private final StringRedisTemplate redis;

  public RedisLiveLocationStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean update(UUID tenantId, DriverLocation location) {
    Long result = redis.execute(
        UPDATE, List.of(geoKey(tenantId), metadataKey(tenantId)), location.shiftId().toString(),
        Double.toString(location.point().longitude()), Double.toString(location.point().latitude()),
        Long.toString(location.sequence()), Long.toString(location.recordedAt().toEpochMilli()));
    return Long.valueOf(1).equals(result);
  }

  @Override
  public List<UUID> nearby(
      UUID tenantId, GeoPoint point, double radiusMeters, int limit, Instant now, Duration maxAge) {
    int boundedLimit = Math.max(1, Math.min(limit, 100));
    RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
        .newGeoRadiusArgs().sortAscending().limit(boundedLimit);
    var results = redis.opsForGeo().radius(
        geoKey(tenantId),
        new Circle(new Point(point.longitude(), point.latitude()),
            new Distance(radiusMeters / 1000.0, Metrics.KILOMETERS)),
        args);
    if (results == null) {
      return List.of();
    }
    Instant cutoff = now.minus(maxAge);
    List<UUID> fresh = new ArrayList<>();
    for (var result : results) {
      String member = result.getContent().getName();
      String metadata = (String) redis.opsForHash().get(metadataKey(tenantId), member);
      if (metadata != null) {
        int separator = metadata.indexOf(':');
        if (separator > 0
            && Instant.ofEpochMilli(Long.parseLong(metadata.substring(separator + 1))).isAfter(cutoff)) {
          fresh.add(UUID.fromString(member));
        }
      }
    }
    return List.copyOf(fresh);
  }

  private String geoKey(UUID tenantId) {
    return "cab:{" + tenantId + "}:driver-locations:geo";
  }

  private String metadataKey(UUID tenantId) {
    return "cab:{" + tenantId + "}:driver-locations:metadata";
  }
}

package in.rsh.cab.location.internal.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.geography.GeoPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisLiveLocationStoreTest {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void overscansPastStaleCandidatesIncludesBoundaryAndPrunesInvalidEntries() {
    UUID tenant = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-08T10:00:00Z");
    Instant cutoff = now.minusSeconds(120);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    GeoOperations<String, String> geo = mock(GeoOperations.class);
    HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    when(redis.opsForGeo()).thenReturn(geo);
    doReturn(hashes).when(redis).opsForHash();

    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> candidates = new ArrayList<>();
    Map<String, String> metadata = new HashMap<>();
    for (int index = 0; index < 5; index++) {
      String member = UUID.randomUUID().toString();
      candidates.add(result(member));
      metadata.put(member, "1:" + cutoff.minusSeconds(1).toEpochMilli());
    }
    String malformed = "not-a-uuid";
    candidates.add(result(malformed));
    metadata.put(malformed, "invalid");
    List<UUID> expected = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    for (UUID shift : expected) {
      candidates.add(result(shift.toString()));
      metadata.put(shift.toString(), "2:" + cutoff.toEpochMilli());
    }
    when(hashes.get(any(), any())).thenAnswer(invocation -> metadata.get(invocation.getArgument(1)));
    when(geo.radius(any(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
        .thenReturn(new GeoResults<>(candidates));
    when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

    List<UUID> nearby = new RedisLiveLocationStore(redis).nearby(tenant,
        new GeoPoint(12.95, 77.6), 5000, 3, now, Duration.ofMinutes(2));

    assertEquals(expected, nearby);
    ArgumentCaptor<RedisGeoCommands.GeoRadiusCommandArgs> args =
        ArgumentCaptor.forClass(RedisGeoCommands.GeoRadiusCommandArgs.class);
    verify(geo).radius(any(), any(Circle.class), args.capture());
    assertEquals(15, args.getValue().getLimit());
    verify(redis).execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
  }

  private GeoResult<RedisGeoCommands.GeoLocation<String>> result(String member) {
    return new GeoResult<>(new RedisGeoCommands.GeoLocation<>(member, new Point(0, 0)),
        new Distance(0.001, Metrics.KILOMETERS));
  }
}

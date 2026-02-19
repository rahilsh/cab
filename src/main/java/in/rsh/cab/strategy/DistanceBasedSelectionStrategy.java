package in.rsh.cab.strategy;

import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.service.RedisGeoService;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("distanceBasedSelectionStrategy")
public class DistanceBasedSelectionStrategy implements CabSelectionStrategy {

  public static final String NAME = "DISTANCE";

  private final RedisGeoService redisGeoService;

  @Autowired
  public DistanceBasedSelectionStrategy(RedisGeoService redisGeoService) {
    this.redisGeoService = redisGeoService;
  }

  @Override
  public Comparator<Cab> getComparator(Location pickupLocation) {
    return Comparator.comparingLong(
        cab -> {
          Location cabLocation = cab.getLocation();
          if (cabLocation == null || pickupLocation == null) {
            return Long.MAX_VALUE;
          }
          return calculateDistanceInMeters(cab.getCabId(), pickupLocation);
        });
  }

  @Override
  public String getName() {
    return NAME;
  }

  private long calculateDistanceInMeters(Integer cabId, Location to) {
    return redisGeoService.getDistanceInMeters(cabId, to);
  }
}

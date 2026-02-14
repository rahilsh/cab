package in.rsh.cab.admin.store;

import in.rsh.cab.commons.model.Cab;
import org.springframework.stereotype.Service;

import in.rsh.cab.admin.exception.CabNotAvailableException;
import in.rsh.cab.admin.exception.NotFoundException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static in.rsh.cab.commons.model.Cab.CabStatus;

@Service
public class CabStore {

  private final Map<Integer, Cab> cabs = new ConcurrentHashMap<>();
  private final AtomicInteger globalId = new AtomicInteger(0);

  public void add(Integer driverId, Integer cityId, String model, CabStatus state) {
    Integer cabId = globalId.incrementAndGet();
    cabs.put(
        cabId,
        Cab.builder()
            .cabId(String.valueOf(cabId))
            .driverId(String.valueOf(driverId))
            .model(model)
            .cityId(cityId)
            .status(state)
            .idleFrom(System.currentTimeMillis())
            .build());
  }

  public Collection<Cab> cabs() {
    return cabs.values();
  }

  public synchronized Cab reserveMostSuitableCab(Integer fromCity, Integer toCity) {
    List<Cab> idleCabs =
        cabs.values().stream()
            .filter(cab -> cab.getCityId().equals(fromCity))
            .filter(cab -> cab.getStatus().equals(CabStatus.AVAILABLE))
            .sorted(
                Comparator.comparingLong(
                    cab -> cab.getIdleFrom() == null ? Long.MAX_VALUE : cab.getIdleFrom()))
            .toList();
    if (idleCabs.isEmpty()) {
      throw new CabNotAvailableException("No cabs available");
    }
    Cab cab = idleCabs.get(0);
    cab.setStatus(CabStatus.UNAVAILABLE);
    if (toCity != null) {
      cab.setCityId(toCity);
    }
    return cab;
  }

  public void update(Integer cabId, Integer cityId, CabStatus state, Long idleFrom) {
    Cab cab = cabs.get(cabId);
    if (cab == null) {
      throw new NotFoundException("Cab does not exist");
    }
    if (cityId != null) {
      cab.setCityId(cityId);
    }
    if (state != null) {
      cab.setStatus(state);
    }
    if (idleFrom != null) {
      cab.setIdleFrom(idleFrom);
    }
  }
}

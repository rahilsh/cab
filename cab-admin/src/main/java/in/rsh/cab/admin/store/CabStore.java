package in.rsh.cab.admin.store;

import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Location;
import in.rsh.cab.commons.state.CabState;
import in.rsh.cab.commons.state.CabStateFactory;
import in.rsh.cab.commons.strategy.CabSelectionStrategy;
import in.rsh.cab.commons.strategy.IdleTimeSelectionStrategy;
import org.springframework.stereotype.Service;

import in.rsh.cab.admin.exception.CabNotAvailableException;
import in.rsh.cab.admin.exception.NotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static in.rsh.cab.commons.model.Cab.CabStatus;

@Service
public class CabStore {

  private final Map<Integer, Cab> cabs = new ConcurrentHashMap<>();
  private final AtomicInteger globalId = new AtomicInteger(0);
  private CabSelectionStrategy selectionStrategy = new IdleTimeSelectionStrategy();

  public void setSelectionStrategy(CabSelectionStrategy selectionStrategy) {
    this.selectionStrategy = selectionStrategy;
  }

  public CabSelectionStrategy getSelectionStrategy() {
    return selectionStrategy;
  }

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
    return reserveMostSuitableCab(fromCity, toCity, null);
  }

  public synchronized Cab reserveMostSuitableCab(
      Integer fromCity, Integer toCity, Location pickupLocation) {
    List<Cab> idleCabs =
        cabs.values().stream()
            .filter(cab -> cab.getCityId().equals(fromCity))
            .filter(cab -> cab.getStatus().equals(CabStatus.AVAILABLE))
            .sorted(selectionStrategy.getComparator(pickupLocation))
            .toList();
    if (idleCabs.isEmpty()) {
      throw new CabNotAvailableException("No cabs available");
    }
    Cab cab = idleCabs.get(0);
    CabState state = CabStateFactory.getState(cab.getStatus());
    state.makeUnavailable(cab);
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
      CabState cabState = CabStateFactory.getState(cab.getStatus());
      if (state == CabStatus.AVAILABLE) {
        cabState.makeAvailable(cab);
      } else if (state == CabStatus.UNAVAILABLE) {
        cabState.makeUnavailable(cab);
      } else if (state == CabStatus.ON_RIDE) {
        cabState.startRide(cab);
      }
    }
    if (idleFrom != null) {
      cab.setIdleFrom(idleFrom);
    }
  }
}

package in.rsh.cab.admin.store;

import static in.rsh.cab.commons.model.Cab.*;

import in.rsh.cab.commons.model.Cab;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CabStore {

  private static final Map<Integer, Cab> cabs = new HashMap<>();
  private static int globalId = 0;

  public void add(Integer driverId, Integer cityId, String model, CabStatus state) {
    Integer cabId = ++globalId;
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

  public void update(Integer cabId, Integer cityId, CabStatus state, Long idleFrom) {
    Cab cab = cabs.get(cabId);
    if (cab == null) {
      throw new RuntimeException("No Such cab");
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

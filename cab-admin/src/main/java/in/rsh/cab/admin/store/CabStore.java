package in.rsh.cab.admin.store;

import in.rsh.cab.admin.model.Cab;
import in.rsh.cab.admin.model.Cab.State;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CabStore {

  private static final Map<Integer, Cab> cabs = new HashMap<>();
  private static int globalId = 0;

  public void add(Integer driverId, Integer cityId, String model, State state) {
    Integer cabId = ++globalId;
    cabs.put(cabId, new Cab(cabId, driverId, cityId, model, state, System.currentTimeMillis()));
  }

  public Collection<Cab> cabs() {
    return cabs.values();
  }

  public void update(Integer cabId, Integer cityId, State state, Long idleFrom) {
    Cab cab = cabs.get(cabId);
    if (cab == null) {
      throw new RuntimeException("No Such cab");
    }
    if (cityId != null) {
      cab.setCityId(cityId);
    }
    if (state != null) {
      cab.setState(state);
    }
    if (idleFrom != null) {
      cab.setIdleFrom(idleFrom);
    }
  }
}

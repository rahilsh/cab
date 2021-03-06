package in.rsh.cab.admin.service;

import in.rsh.cab.admin.model.Cab;
import in.rsh.cab.admin.model.Cab.State;
import in.rsh.cab.admin.store.CabStore;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CabsService {

  private final CabStore cabStore;
  private final CityService cityService;

  @Autowired
  public CabsService(CabStore cabStore, CityService cityService) {
    this.cabStore = cabStore;
    this.cityService = cityService;
  }

  public void addCab(Integer driverId, Integer cityId, String model) {
    // checkIfDriverIsValid(driverId);
    // checkIfModelIsValid(model);
    cityService.validateCityOrThrow(cityId);
    cabStore.add(driverId, cityId, model, State.IDLE);
  }

  public Collection<Cab> getAllCabs() {
    return cabStore.cabs();
  }

  public void updateCab(Integer cabId, Integer cityId, State state) {
    cityService.validateCityOrThrow(cityId);
    update(cabId, cityId, state);
  }

  public List<Cab> getIdleCabsInCity(Integer fromCity) {
    return cabStore.cabs().stream()
        .filter(cab -> cab.getCityId().equals(fromCity))
        .filter(cab -> cab.getState().equals(State.IDLE))
        .collect(Collectors.toList());
  }

  public Cab getMostSuitableCab(Integer fromCity) {
    List<Cab> idleCabs = getIdleCabsInCity(fromCity);
    if (idleCabs == null || idleCabs.isEmpty()) {
      throw new RuntimeException("No cabs Available");
    }
    idleCabs.sort((a, b) -> (int) (a.getIdleFrom() - b.getIdleFrom()));
    return idleCabs.get(0);
  }

  public void update(Integer cabId, Integer cityId, State state) {
    cabStore.update(cabId, cityId, state, null);
  }

  public void update(Integer cabId, State state, Long idleFrom) {
    cabStore.update(cabId, null, state, idleFrom);
  }
}

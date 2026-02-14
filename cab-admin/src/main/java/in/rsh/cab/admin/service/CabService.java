package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.CabNotAvailableException;
import in.rsh.cab.admin.store.CabStore;
import in.rsh.cab.commons.model.Cab;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static in.rsh.cab.commons.model.Cab.CabStatus;

@Service
public class CabService {

  private final CabStore cabStore;
  private final CityService cityService;

  @Autowired
  public CabService(CabStore cabStore, CityService cityService) {
    this.cabStore = cabStore;
    this.cityService = cityService;
  }

  public void addCab(Integer driverId, Integer cityId, String model) {
    /* TODO: checkIfDriverIsValid(driverId);
     * TODO: checkIfModelIsValid(model);
     */
    cityService.validateCityOrThrow(cityId);
    cabStore.add(driverId, cityId, model, CabStatus.AVAILABLE);
  }

  public Collection<Cab> getAllCabs() {
    return cabStore.cabs();
  }

  public void updateCab(Integer cabId, Integer cityId, CabStatus state) {
    cityService.validateCityOrThrow(cityId);
    update(cabId, cityId, state);
  }

  public List<Cab> getIdleCabsInCity(Integer fromCity) {
    return cabStore.cabs().stream()
        .filter(cab -> cab.getCityId().equals(fromCity))
        .filter(cab -> cab.getStatus().equals(CabStatus.AVAILABLE))
        .collect(Collectors.toList());
  }

  public Cab getMostSuitableCab(Integer fromCity) {
    List<Cab> idleCabs = getIdleCabsInCity(fromCity);
    if (idleCabs == null || idleCabs.isEmpty()) {
      throw new CabNotAvailableException("No cabs available");
    }
    idleCabs.sort(
        Comparator.comparingLong(
            cab -> cab.getIdleFrom() == null ? Long.MAX_VALUE : cab.getIdleFrom()));
    return idleCabs.get(0);
  }

  public Cab reserveMostSuitableCab(Integer fromCity, Integer toCity) {
    return cabStore.reserveMostSuitableCab(fromCity, toCity);
  }

  public void update(Integer cabId, Integer cityId, CabStatus state) {
    cabStore.update(cabId, cityId, state, null);
  }

  public void update(Integer cabId, CabStatus state, Long idleFrom) {
    cabStore.update(cabId, null, state, idleFrom);
  }
}

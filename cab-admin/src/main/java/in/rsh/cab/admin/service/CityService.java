package in.rsh.cab.admin.service;

import in.rsh.cab.admin.model.City;
import in.rsh.cab.admin.store.CityStore;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CityService {

  private final CityStore cityStore;

  @Autowired
  public CityService(CityStore cityStore) {
    this.cityStore = cityStore;
  }

  public void addCity(String name) {
    cityStore.add(name);
  }

  public Collection<City> getAllCities() {
    return cityStore.cities();
  }

  public void validateCityOrThrow(Integer cityId) {
    if (cityStore.getCity(cityId) == null) {
      throw new IllegalArgumentException("City does not exists");
    }
  }
}

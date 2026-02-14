package in.rsh.cab.admin.service;

import in.rsh.cab.admin.exception.NotFoundException;
import in.rsh.cab.admin.model.City;
import in.rsh.cab.admin.store.CityStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

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
      throw new NotFoundException("City does not exist");
    }
  }
}

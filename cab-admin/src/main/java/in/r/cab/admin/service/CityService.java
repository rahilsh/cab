package in.r.cab.admin.service;

import in.r.cab.admin.model.City;
import in.r.cab.admin.store.CityStore;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CityService {

  @Autowired
  CityStore cityStore;

  public void addCity(String name) {
    cityStore.add(name);
  }

  public Collection<City> getAllCities() {
    return cityStore.cities();
  }

  public void validateCityOrThrow(Integer cityId) {
    if (cityStore.getCity(cityId) == null) {
      throw new RuntimeException("City does not exists");
    }
  }
}

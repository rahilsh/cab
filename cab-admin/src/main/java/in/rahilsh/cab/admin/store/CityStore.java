package in.rahilsh.cab.admin.store;

import in.rahilsh.cab.admin.model.City;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CityStore {

  static Map<Integer, City> cities = new HashMap<>();
  static int globalId = 0;

  public void add(String name) {
    Integer cityId = ++globalId;
    // throwErrorIfCityExistsWithSameName
    cities.put(cityId, new City(cityId, name));
  }

  public Collection<City> cities() {
    return cities.values();
  }

  public City getCity(Integer cityId) {
    return cities.get(cityId);
  }
}

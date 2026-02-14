package in.rsh.cab.admin.store;

import in.rsh.cab.admin.model.City;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CityStore {

  private final Map<Integer, City> cities = new ConcurrentHashMap<>();
  private final AtomicInteger globalId = new AtomicInteger(0);

  public void add(String name) {
    Integer cityId = globalId.incrementAndGet();
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

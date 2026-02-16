package in.rsh.cab.service;

import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.entity.CityEntity;
import in.rsh.cab.model.City;
import in.rsh.cab.repository.CityJpaRepository;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CityService {

  private final CityJpaRepository cityJpaRepository;

  @Autowired
  public CityService(CityJpaRepository cityJpaRepository) {
    this.cityJpaRepository = cityJpaRepository;
  }

  public void addCity(String name) {
    cityJpaRepository.save(new CityEntity(name));
  }

  public Collection<City> getAllCities() {
    return cityJpaRepository.findAll().stream().map(this::toModel).toList();
  }

  public void validateCityOrThrow(Integer cityId) {
    if (cityJpaRepository.findById(cityId).isEmpty()) {
      throw new NotFoundException("City does not exist");
    }
  }

  private City toModel(CityEntity entity) {
    if (entity == null) {
      return null;
    }
    return new City(entity.getId(), entity.getName());
  }
}

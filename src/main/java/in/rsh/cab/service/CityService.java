package in.rsh.cab.service;

import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.mapper.CityMapper;
import in.rsh.cab.model.City;
import in.rsh.cab.model.response.CityResponse;
import in.rsh.cab.repository.CityJpaRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CityService {

  private final CityJpaRepository cityJpaRepository;
  private final CityMapper cityMapper;

  @Autowired
  public CityService(CityJpaRepository cityJpaRepository, CityMapper cityMapper) {
    this.cityJpaRepository = cityJpaRepository;
    this.cityMapper = cityMapper;
  }

  public void addCity(String name) {
    cityJpaRepository.save(new in.rsh.cab.entity.CityEntity(name));
  }

  public Collection<City> getAllCities() {
    return cityJpaRepository.findAll().stream().map(cityMapper::toModel).toList();
  }

  public List<CityResponse> getAllCitiesResponse() {
    return cityJpaRepository.findAll().stream()
        .map(cityMapper::toModel)
        .map(cityMapper::toResponse)
        .toList();
  }

  public void validateCityOrThrow(Integer cityId) {
    if (cityJpaRepository.findById(cityId).isEmpty()) {
      throw new NotFoundException("City does not exist");
    }
  }
}

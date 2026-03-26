package in.rsh.cab.service;

import static in.rsh.cab.model.Cab.CabStatus;

import in.rsh.cab.entity.CabEntity;
import in.rsh.cab.exception.CabNotAvailableException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.mapper.CabMapper;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.model.response.CabResponse;
import in.rsh.cab.repository.CabJpaRepository;
import in.rsh.cab.state.CabState;
import in.rsh.cab.state.CabStateFactory;
import in.rsh.cab.strategy.CabSelectionStrategy;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CabService {

  private final CabJpaRepository cabJpaRepository;
  private final CityService cityService;
  private final RedisGeoService redisGeoService;
  private final CabMapper cabMapper;
  private CabSelectionStrategy selectionStrategy;

  @Autowired
  public CabService(CabJpaRepository cabJpaRepository, CityService cityService, 
      RedisGeoService redisGeoService,
      @Qualifier("distanceBasedSelectionStrategy") CabSelectionStrategy selectionStrategy,
      CabMapper cabMapper) {
    this.cabJpaRepository = cabJpaRepository;
    this.cityService = cityService;
    this.redisGeoService = redisGeoService;
    this.selectionStrategy = selectionStrategy;
    this.cabMapper = cabMapper;
  }

  public void addCab(Integer driverId, Integer cityId, String model) {
    cityService.validateCityOrThrow(cityId);
    CabEntity entity = CabEntity.builder()
        .driverId(String.valueOf(driverId))
        .model(model)
        .cityId(cityId)
        .status(CabEntity.CabStatus.AVAILABLE)
        .idleFrom(System.currentTimeMillis())
        .build();
    cabJpaRepository.save(entity);
  }

  public Page<CabResponse> getAllCabs(Pageable pageable) {
    return cabJpaRepository.findAll(pageable)
        .map(cabMapper::toModel)
        .map(cabMapper::toResponse);
  }

  public void updateCab(int cabId, Integer cityId, CabStatus state) {
    cityService.validateCityOrThrow(cityId);
    update(cabId, cityId, state, null);
  }

  public List<Cab> getIdleCabsInCity(Integer fromCity) {
    return cabJpaRepository.findByCityIdAndStatus(fromCity, CabEntity.CabStatus.AVAILABLE)
        .stream()
        .map(cabMapper::toModel)
        .toList();
  }

  @Transactional
  public Cab reserveMostSuitableCab(Integer fromCity, Integer toCity) {
    return reserveMostSuitableCab(fromCity, toCity, null);
  }

  @Transactional
  public Cab reserveMostSuitableCab(
      Integer fromCity, Integer toCity, Location pickupLocation) {
    List<CabEntity> idleCabs = cabJpaRepository.findAvailableCabsInCityWithLock(fromCity).stream()
        .sorted((c1, c2) -> {
          Cab cab1 = cabMapper.toModel(c1);
          Cab cab2 = cabMapper.toModel(c2);
          return selectionStrategy.getComparator(pickupLocation).compare(cab1, cab2);
        })
        .toList();
    if (idleCabs.isEmpty()) {
      throw new CabNotAvailableException("No cabs available");
    }
    CabEntity entity = idleCabs.get(0);
    Cab cab = cabMapper.toModel(entity);
    CabState state = CabStateFactory.getState(cab.getStatus());
    state.makeUnavailable(cab);
    if (toCity != null) {
      cab.setCityId(toCity);
    }
    CabEntity updatedEntity = cabMapper.toEntity(cab);
    updatedEntity.setId(entity.getId());
    cabJpaRepository.save(updatedEntity);
    return cab;
  }

  public void update(int cabId, Integer cityId, CabStatus state, Long idleFrom) {
    Optional<CabEntity> optionalEntity = cabJpaRepository.findById(cabId);
    if (optionalEntity.isEmpty()) {
      throw new NotFoundException("Cab does not exist");
    }
    CabEntity entity = optionalEntity.get();
    if (cityId != null) {
      entity.setCityId(cityId);
    }
    if (state != null) {
      Cab cab = cabMapper.toModel(entity);
      CabState cabState = CabStateFactory.getState(cab.getStatus());
      if (state == Cab.CabStatus.AVAILABLE) {
        cabState.makeAvailable(cab);
      } else if (state == Cab.CabStatus.UNAVAILABLE) {
        cabState.makeUnavailable(cab);
      } else if (state == Cab.CabStatus.ON_RIDE) {
        cabState.startRide(cab);
      }
      entity.setStatus(CabEntity.CabStatus.valueOf(cab.getStatus().name()));
    }
    if (idleFrom != null) {
      entity.setIdleFrom(idleFrom);
    }
    cabJpaRepository.save(entity);
  }

  public boolean updateCabLocation(int cabId, Location location) {
    Optional<CabEntity> optionalEntity = cabJpaRepository.findById(cabId);
    if (optionalEntity.isEmpty()) {
      return false;
    }
    CabEntity entity = optionalEntity.get();
    entity.setLocationX(location.latitude());
    entity.setLocationY(location.longitude());
    cabJpaRepository.save(entity);
    redisGeoService.updateCabLocation(cabId, location);
    return true;
  }

  public Optional<Cab> getCabByDriver(String driverId) {
    return cabJpaRepository.findAll().stream()
        .filter(cab -> cab.getDriverId().equals(driverId))
        .map(cabMapper::toModel)
        .findFirst();
  }

  public void updateCabStatus(int cabId, CabStatus cabStatus) {
    update(cabId, null, cabStatus, null);
  }
}

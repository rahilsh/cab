package in.rsh.cab.service;

import static in.rsh.cab.model.Cab.CabStatus;

import in.rsh.cab.exception.CabNotAvailableException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.entity.CabEntity;
import in.rsh.cab.repository.CabJpaRepository;
import in.rsh.cab.state.CabState;
import in.rsh.cab.state.CabStateFactory;
import in.rsh.cab.strategy.CabSelectionStrategy;
import in.rsh.cab.strategy.IdleTimeSelectionStrategy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CabService {

  private final CabJpaRepository cabJpaRepository;
  private final CityService cityService;
  private final AtomicInteger globalId = new AtomicInteger(0);
  private CabSelectionStrategy selectionStrategy = new IdleTimeSelectionStrategy();

  @Autowired
  public CabService(CabJpaRepository cabJpaRepository, CityService cityService) {
    this.cabJpaRepository = cabJpaRepository;
    this.cityService = cityService;
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

  public Collection<Cab> getAllCabs() {
    return cabJpaRepository.findAll().stream().map(this::toModel).toList();
  }

  public void updateCab(int cabId, Integer cityId, CabStatus state) {
    cityService.validateCityOrThrow(cityId);
    update(cabId, cityId, state, null);
  }

  public List<Cab> getIdleCabsInCity(Integer fromCity) {
    return cabJpaRepository.findByCityId(fromCity).stream()
        .filter(cab -> cab.getStatus().equals(CabEntity.CabStatus.AVAILABLE))
        .map(this::toModel)
        .toList();
  }

  @Transactional
  public Cab reserveMostSuitableCab(Integer fromCity, Integer toCity) {
    return reserveMostSuitableCab(fromCity, toCity, null);
  }

  @Transactional
  public Cab reserveMostSuitableCab(
      Integer fromCity, Integer toCity, Location pickupLocation) {
    List<CabEntity> idleCabs = cabJpaRepository.findByCityId(fromCity).stream()
        .filter(cab -> cab.getStatus().equals(CabEntity.CabStatus.AVAILABLE))
        .sorted((c1, c2) -> {
          Cab cab1 = toModel(c1);
          Cab cab2 = toModel(c2);
          return selectionStrategy.getComparator(pickupLocation).compare(cab1, cab2);
        })
        .toList();
    if (idleCabs.isEmpty()) {
      throw new CabNotAvailableException("No cabs available");
    }
    CabEntity entity = idleCabs.get(0);
    Cab cab = toModel(entity);
    CabState state = CabStateFactory.getState(cab.getStatus());
    state.makeUnavailable(cab);
    if (toCity != null) {
      cab.setCityId(toCity);
    }
    CabEntity updatedEntity = toEntity(cab);
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
      Cab cab = toModel(entity);
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
    return true;
  }

  public Optional<Cab> getCabByDriver(String driverId) {
    return cabJpaRepository.findAll().stream()
        .filter(cab -> cab.getDriverId().equals(driverId))
        .map(this::toModel)
        .findFirst();
  }

  public boolean updateCabStatus(int cabId, CabStatus cabStatus) {
    Optional<CabEntity> optionalEntity = cabJpaRepository.findById(cabId);
    if (optionalEntity.isEmpty()) {
      return false;
    }
    CabEntity entity = optionalEntity.get();
    entity.setStatus(CabEntity.CabStatus.valueOf(cabStatus.name()));
    cabJpaRepository.save(entity);
    return true;
  }

  private Cab toModel(CabEntity entity) {
    if (entity == null) {
      return null;
    }
    return Cab.builder()
        .cabId(entity.getId())
        .driverId(entity.getDriverId())
        .cabNumber(entity.getCabNumber())
        .status(Cab.CabStatus.valueOf(entity.getStatus().name()))
        .type(entity.getType() != null ? Cab.CabType.valueOf(entity.getType().name()) : null)
        .location(entity.getLocationX() != null && entity.getLocationY() != null
            ? new Location(entity.getLocationX(), entity.getLocationY()) : null)
        .idleFrom(entity.getIdleFrom())
        .cityId(entity.getCityId())
        .model(entity.getModel())
        .build();
  }

  private CabEntity toEntity(Cab model) {
    if (model == null) {
      return null;
    }
    return CabEntity.builder()
        .id(model.getCabId())
        .driverId(model.getDriverId())
        .cabNumber(model.getCabNumber())
        .status(CabEntity.CabStatus.valueOf(model.getStatus().name()))
        .type(model.getType() != null ? CabEntity.CabType.valueOf(model.getType().name()) : null)
        .locationX(model.getLocation() != null ? model.getLocation().latitude() : null)
        .locationY(model.getLocation() != null ? model.getLocation().longitude() : null)
        .idleFrom(model.getIdleFrom())
        .cityId(model.getCityId())
        .model(model.getModel())
        .build();
  }
}

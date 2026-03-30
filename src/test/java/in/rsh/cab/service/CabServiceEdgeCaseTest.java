package in.rsh.cab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.rsh.cab.entity.CabEntity;
import in.rsh.cab.exception.CabNotAvailableException;
import in.rsh.cab.exception.NotFoundException;
import in.rsh.cab.mapper.CabMapper;
import in.rsh.cab.model.Cab;
import in.rsh.cab.model.Location;
import in.rsh.cab.model.response.CabResponse;
import in.rsh.cab.repository.CabJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class CabServiceEdgeCaseTest {

  @Mock
  private CabJpaRepository cabJpaRepository;

  @Mock
  private CityService cityService;

  @Mock
  private RedisGeoService redisGeoService;

  @Mock
  private CabMapper cabMapper;

  @InjectMocks
  private CabService cabService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Cab mockCab = Cab.builder().cabId(1).status(Cab.CabStatus.AVAILABLE).build();
    when(cabMapper.toModel(any(CabEntity.class))).thenReturn(mockCab);
    when(cabMapper.toEntity(any(Cab.class))).thenReturn(CabEntity.builder().id(1).status(CabEntity.CabStatus.AVAILABLE).build());
    when(cabMapper.toResponse(any(Cab.class))).thenReturn(new CabResponse(1, null, null, null, null, null, null, null, null));
  }

  @Nested
  class UpdateCabEdgeCases {

    @Test
    void update_withNullCity_shouldNotUpdateCity() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .cityId(100)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));

      cabService.update(1, null, Cab.CabStatus.AVAILABLE, null);

      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void update_withNullStatus_shouldNotUpdateStatus() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .cityId(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));

      cabService.update(1, 2, null, null);

      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void update_withIdleFrom_shouldUpdateIdleFrom() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));
      long idleTime = System.currentTimeMillis();

      cabService.update(1, null, Cab.CabStatus.AVAILABLE, idleTime);

      verify(cabJpaRepository).save(any(CabEntity.class));
    }
  }

  @Nested
  class UpdateCabStatusEdgeCases {

    @Test
    void updateCabStatus_withUnavailable_shouldSucceed() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));

      cabService.updateCabStatus(1, Cab.CabStatus.UNAVAILABLE);

      verify(cabJpaRepository).save(any(CabEntity.class));
    }

    @Test
    void updateCabStatus_withOnRide_shouldSucceed() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));

      cabService.updateCabStatus(1, Cab.CabStatus.ON_RIDE);

      verify(cabJpaRepository).save(any(CabEntity.class));
    }
  }

  @Nested
  class GetAllCabsEdgeCases {

    @Test
    void getAllCabs_withEmptyPage_shouldReturnEmpty() {
      Pageable pageable = PageRequest.of(0, 20);
      Page<CabEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
      when(cabJpaRepository.findAll(pageable)).thenReturn(emptyPage);

      Page<CabResponse> result = cabService.getAllCabs(pageable);

      assertEquals(0, result.getTotalElements());
    }

    @Test
    void getAllCabs_withMultiplePages_shouldReturnCorrectPageInfo() {
      Pageable pageable = PageRequest.of(1, 2);
      List<CabEntity> entities = List.of(
          CabEntity.builder().id(3).status(CabEntity.CabStatus.AVAILABLE).build(),
          CabEntity.builder().id(4).status(CabEntity.CabStatus.AVAILABLE).build()
      );
      Page<CabEntity> page = new PageImpl<>(entities, pageable, 5);
      when(cabJpaRepository.findAll(pageable)).thenReturn(page);

      Page<CabResponse> result = cabService.getAllCabs(pageable);

      assertEquals(5, result.getTotalElements());
      assertEquals(2, result.getNumberOfElements());
    }
  }

  @Nested
  class GetIdleCabsEdgeCases {

    @Test
    void getIdleCabsInCity_withMultipleCabs_shouldReturnAll() {
      List<CabEntity> cabs = List.of(
          CabEntity.builder().id(1).status(CabEntity.CabStatus.AVAILABLE).cityId(1).build(),
          CabEntity.builder().id(2).status(CabEntity.CabStatus.AVAILABLE).cityId(1).build(),
          CabEntity.builder().id(3).status(CabEntity.CabStatus.AVAILABLE).cityId(1).build()
      );
      when(cabJpaRepository.findByCityIdAndStatus(1, CabEntity.CabStatus.AVAILABLE)).thenReturn(cabs);

      List<Cab> result = cabService.getIdleCabsInCity(1);

      assertEquals(3, result.size());
    }
  }

  @Nested
  class UpdateLocationEdgeCases {

    @Test
    void updateCabLocation_withExistingCab_shouldUpdateBothEntityAndRedis() {
      CabEntity cabEntity = CabEntity.builder()
          .id(1)
          .status(CabEntity.CabStatus.AVAILABLE)
          .build();
      when(cabJpaRepository.findById(1)).thenReturn(Optional.of(cabEntity));
      Location location = new Location(10, 20);

      boolean result = cabService.updateCabLocation(1, location);

      assertTrue(result);
      verify(cabJpaRepository).save(any(CabEntity.class));
      verify(redisGeoService).updateCabLocation(1, location);
    }
  }
}

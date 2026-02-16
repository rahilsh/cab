package in.rsh.cab.admin.service;

import in.rsh.cab.commons.entity.CabEntity;
import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.repository.CabJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CabServiceTest {

    @Mock
    private CabJpaRepository cabJpaRepository;

    @Mock
    private CityService cityService;

    @InjectMocks
    private CabService cabService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    class AddCabTests {

        @Test
        void addCab_shouldAddCabToStore() {
            doNothing().when(cityService).validateCityOrThrow(1);

            cabService.addCab(1, 1, "Toyota Innova");

            verify(cabJpaRepository).save(any(CabEntity.class));
        }

        @Test
        void addCab_withInvalidCity_shouldThrowException() {
            doThrow(new in.rsh.cab.admin.exception.NotFoundException("City does not exist"))
                    .when(cityService).validateCityOrThrow(999);

            assertThrows(in.rsh.cab.admin.exception.NotFoundException.class,
                    () -> cabService.addCab(1, 999, "Toyota Innova"));
        }
    }

    @Nested
    class GetAllCabsTests {

        @Test
        void getAllCabs_shouldReturnAllCabs() {
            when(cabJpaRepository.findAll()).thenReturn(List.of());

            var cabs = cabService.getAllCabs();

            assertNotNull(cabs);
            verify(cabJpaRepository).findAll();
        }
    }

    @Nested
    class UpdateCabTests {

        @Test
        void updateCab_shouldUpdateCabStatus() {
            doNothing().when(cityService).validateCityOrThrow(1);
            CabEntity cabEntity = new CabEntity();
            cabEntity.setStatus(CabEntity.CabStatus.AVAILABLE);
            when(cabJpaRepository.findByCabId(anyString())).thenReturn(Optional.of(cabEntity));

            cabService.updateCab(1, 1, Cab.CabStatus.UNAVAILABLE);

            verify(cabJpaRepository).save(any(CabEntity.class));
        }

        @Test
        void updateCab_withInvalidCity_shouldThrowException() {
            doThrow(new in.rsh.cab.admin.exception.NotFoundException("City does not exist"))
                    .when(cityService).validateCityOrThrow(999);

            assertThrows(in.rsh.cab.admin.exception.NotFoundException.class,
                    () -> cabService.updateCab(1, 999, Cab.CabStatus.AVAILABLE));
        }
    }

    @Nested
    class GetIdleCabsInCityTests {

        @Test
        void getIdleCabsInCity_shouldReturnIdleCabs() {
            CabEntity cab = CabEntity.builder()
                    .cabId("1")
                    .status(CabEntity.CabStatus.AVAILABLE)
                    .cityId(1)
                    .build();
            when(cabJpaRepository.findByCityId(1)).thenReturn(List.of(cab));

            var idleCabs = cabService.getIdleCabsInCity(1);

            assertEquals(1, idleCabs.size());
        }

        @Test
        void getIdleCabsInCity_withNoIdleCabs_shouldReturnEmptyList() {
            CabEntity cab = CabEntity.builder()
                    .cabId("1")
                    .status(CabEntity.CabStatus.UNAVAILABLE)
                    .cityId(1)
                    .build();
            when(cabJpaRepository.findByCityId(1)).thenReturn(List.of(cab));

            var idleCabs = cabService.getIdleCabsInCity(1);

            assertTrue(idleCabs.isEmpty());
        }
    }

    @Nested
    class ReserveMostSuitableCabTests {

        @Test
        void reserveMostSuitableCab_shouldCallStore() {
            CabEntity cab = CabEntity.builder()
                    .cabId("1")
                    .status(CabEntity.CabStatus.AVAILABLE)
                    .cityId(1)
                    .build();
            when(cabJpaRepository.findByCityId(1)).thenReturn(List.of(cab));

            var result = cabService.reserveMostSuitableCab(1, 2);

            assertNotNull(result);
            verify(cabJpaRepository).save(any(CabEntity.class));
        }
    }

    @Nested
    class UpdateWithIdleFromTests {

        @Test
        void update_withIdleFrom_shouldUpdateCab() {
            CabEntity cabEntity = new CabEntity();
            cabEntity.setStatus(CabEntity.CabStatus.AVAILABLE);
            when(cabJpaRepository.findByCabId(anyString())).thenReturn(Optional.of(cabEntity));
            cabService.update(1, 1000, Cab.CabStatus.AVAILABLE, System.currentTimeMillis());

            verify(cabJpaRepository).save(any(CabEntity.class));
        }
    }
}

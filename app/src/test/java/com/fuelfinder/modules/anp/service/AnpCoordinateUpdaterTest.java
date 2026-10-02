package com.fuelfinder.modules.anp.service;

import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnpCoordinateUpdaterTest {

    private static final String WITH_COORDINATES = "12345678000195";
    private static final String PRESERVE_COORDINATES = "12345678000196";

    @Mock
    private StationRepository stationRepository;

    @Test
    void updatesValidApiCoordinatesAndPreservesExistingValuesWhenApiCoordinatesAreMissing() {
        Station first = station(WITH_COORDINATES, null, null);
        Station second = station(
                PRESERVE_COORDINATES,
                new BigDecimal("-23.1"),
                new BigDecimal("-46.1"));
        lenient().when(stationRepository.findByCnpjIn(anyCollection()))
                .thenReturn(List.of(first, second));
        when(stationRepository.save(any(Station.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AnpCoordinateUpdater updater = new AnpCoordinateUpdater(stationRepository);

        AnpCoordinateUpdateResult result = updater.update(
                Set.of(WITH_COORDINATES, PRESERVE_COORDINATES),
                Map.of(
                        WITH_COORDINATES,
                        new AnpRetailerRecord(WITH_COORDINATES, "SP", "-23.5", "-46.6"),
                        PRESERVE_COORDINATES,
                        new AnpRetailerRecord(PRESERVE_COORDINATES, "SP", "", null)),
                Set.of(),
                List.of());

        assertEquals(1, result.coordinatesUpdated());
        assertEquals(0, result.apiCnpjsUnmatched());
        assertEquals(1, result.apiStationsWithoutCoordinates());
        assertEquals(0, result.stationsWithoutCoordinates());
        assertEquals(new BigDecimal("-23.5000000"), first.getLatitude());
        assertEquals(new BigDecimal("-46.6000000"), first.getLongitude());
        assertEquals(new BigDecimal("-23.1"), second.getLatitude());
        assertEquals(new BigDecimal("-46.1"), second.getLongitude());
        verify(stationRepository).save(first);
    }

    private Station station(String cnpj, BigDecimal latitude, BigDecimal longitude) {
        Station station = new Station(
                cnpj, "Corporate", "Trade", "Brand",
                "Rua", "1", "Centro", "Sao Paulo", "SP", null,
                latitude, longitude);
        station.setId(UUID.randomUUID());
        return station;
    }
}

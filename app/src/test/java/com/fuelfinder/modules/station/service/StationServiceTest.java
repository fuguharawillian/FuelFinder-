package com.fuelfinder.modules.station.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.DuplicateResourceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.station.dto.CreateStationRequestDTO;
import com.fuelfinder.modules.station.dto.UpdateStationRequestDTO;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.entity.StationStatus;
import com.fuelfinder.modules.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

    private static final UUID STATION_ID = UUID.randomUUID();

    @Mock
    private StationRepository stationRepository;

    @Mock
    private GeoapifyGeocodingService geocodingService;

    private StationService stationService;

    @BeforeEach
    void setUp() {
        stationService = new StationService(stationRepository, geocodingService);
        lenient().when(stationRepository.save(any(Station.class)))
                .thenAnswer(invocation -> {
                    Station station = invocation.getArgument(0);
                    station.setId(STATION_ID);
                    return station;
                });
        lenient().when(geocodingService.geocode(any())).thenReturn(Optional.empty());
    }

    @Test
    void createsStationWithNormalizedCnpjAndDefaultStatus() {
        when(stationRepository.existsByCnpj("12345678000195")).thenReturn(false);

        var response = stationService.create(createRequest(
                "12.345.678/0001-95", "São Paulo", decimal("-23.55"), decimal("-46.63")));

        ArgumentCaptor<Station> captor = ArgumentCaptor.forClass(Station.class);
        verify(stationRepository).save(captor.capture());
        assertEquals("12345678000195", captor.getValue().getCnpj());
        assertEquals("SP", captor.getValue().getState());
        assertEquals(StationStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals("Rua A, 10, Centro", response.address());
        assertEquals("ACTIVE", response.status());
        assertNull(response.distanceKm());
    }

    @Test
    void createsStationWhenOptionalAddressFieldsAreAbsent() {
        when(stationRepository.existsByCnpj("12345678000195")).thenReturn(false);
        CreateStationRequestDTO request = new CreateStationRequestDTO(
                "12345678000195", "Corporate", " ", null, null, null, null,
                "City", "SP", null, decimal("0"), decimal("0"));

        var response = stationService.create(request);

        assertEquals("", response.address());
        assertNull(response.tradeName());
        assertNull(response.brand());
        assertNull(response.postalCode());
    }

    @Test
    void rejectsDuplicateAndMalformedCnpj() {
        when(stationRepository.existsByCnpj("12345678000195")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> stationService.create(
                createRequest("12345678000195", "City", decimal("0"), decimal("0"))));
        assertThrows(BusinessException.class, () -> stationService.create(
                createRequest("abc", "City", decimal("0"), decimal("0"))));
        verify(stationRepository, never()).save(any(Station.class));
    }

    @Test
    void returnsNearbyStationsInAscendingDistanceAndFiltersOutsideRadius() {
        Station nearer = station("Near", decimal("0"), decimal("0.1"));
        Station farther = station("Far", decimal("0"), decimal("0.3"));
        Station outside = station("Outside", decimal("0"), decimal("1"));
        when(stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(farther, outside, nearer));

        var results = stationService.search(0.0, 0.0, 40.0, null);

        assertEquals(List.of("Near", "Far"),
                results.stream().map(result -> result.corporateName()).toList());
        assertEquals(StationStatus.ACTIVE.name(), results.getFirst().status());
        assertEquals(11.12, results.getFirst().distanceKm(), 0.2);
    }

    @Test
    void handlesLongitudeWrapAndPolarSearchBounds() {
        Station east = station("East", decimal("0"), decimal("179.95"));
        Station west = station("West", decimal("0"), decimal("-179.9"));
        when(stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(east), List.of(west));

        assertEquals(2, stationService.search(0.0, 179.9, 50.0, null).size());

        Station nearPole = station("Pole", decimal("89.95"), decimal("120"));
        when(stationRepository.findByStatusAndLatitudeBetween(any(), any(), any()))
                .thenReturn(List.of(nearPole));
        assertEquals(1, stationService.search(89.9, 0.0, 50.0, null).size());

        when(stationRepository.findByStatusAndLatitudeBetween(any(), any(), any()))
                .thenReturn(List.of(nearPole));
        assertEquals(1, stationService.search(0.0, 0.0, 20_100.0, null).size());
    }

    @Test
    void handlesLongitudeWrapAcrossTheWesternAntimeridian() {
        Station west = station("West", decimal("0"), decimal("-179.95"));
        Station east = station("East", decimal("0"), decimal("179.9"));
        when(stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(west), List.of(east));

        var results = stationService.search(0.0, -179.9, 50.0, null);

        assertEquals(2, results.size());
    }

    @Test
    void searchesNearTheSouthPoleAndUsesCoordinatesWhenTextCannotBeGeocoded() {
        Station southPole = station("South Pole", decimal("-89.95"), decimal("120"));
        when(stationRepository.findByStatusAndLatitudeBetween(any(), any(), any()))
                .thenReturn(List.of(southPole));
        assertEquals(1, stationService.search(-89.9, 0.0, 50.0, null).size());

        when(geocodingService.geocode("Unknown area")).thenReturn(Optional.empty());
        when(stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(station("Near coordinates", decimal("0"), decimal("0"))));
        assertEquals("Near coordinates", stationService.search(
                0.0, 0.0, 5.0, "Unknown area").getFirst().corporateName());
    }

    @Test
    void validatesSearchArgumentsAndCoordinatePairs() {
        assertThrows(BusinessException.class,
                () -> stationService.search(0.0, null, 5.0, null));
        assertThrows(BusinessException.class,
                () -> stationService.search(null, 0.0, 5.0, null));
        assertThrows(BusinessException.class,
                () -> stationService.search(null, null, 5.0, null));
        assertThrows(BusinessException.class,
                () -> stationService.search(null, null, null, "  "));
        assertThrows(BusinessException.class,
                () -> stationService.search(0.0, 0.0, 0.0, null));
        assertThrows(BusinessException.class,
                () -> stationService.search(0.0, 0.0, Double.NaN, null));
        assertThrows(BusinessException.class,
                () -> stationService.search(91.0, 0.0, 5.0, null));
        assertThrows(BusinessException.class,
                () -> stationService.search(0.0, 181.0, 5.0, null));
        assertThrows(BusinessException.class,
                () -> stationService.search(Double.NaN, 0.0, 5.0, null));
    }

    @Test
    void usesGeocodingWhenConfiguredAndFallsBackToLocalTextSearch() {
        Station localMatch = station("Posto Central", decimal("-23.55"), decimal("-46.63"));
        when(stationRepository.searchByText(StationStatus.ACTIVE, "Centro"))
                .thenReturn(List.of(localMatch));
        assertEquals("Posto Central",
                stationService.search(null, null, null, " Centro ").getFirst().corporateName());

        when(geocodingService.geocode("Rio de Janeiro"))
                .thenReturn(Optional.of(new GeoapifyGeocodingService.GeoPoint(-22.9, -43.2)));
        when(stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(station("Geocoded", decimal("-22.9"), decimal("-43.2"))));
        assertEquals("Geocoded", stationService.search(
                null, null, 5.0, "Rio de Janeiro").getFirst().corporateName());

        when(geocodingService.geocode("No match"))
                .thenReturn(Optional.empty());
        assertEquals(List.of(), stationService.search(null, null, null, "No match"));

        Station blankAddress = station("Blank", decimal("0"), decimal("0"));
        blankAddress.setStreet("");
        when(stationRepository.searchByText(StationStatus.ACTIVE, "Blank"))
                .thenReturn(List.of(blankAddress));
        assertEquals("", stationService.search(null, null, null, "Blank")
                .getFirst().address());

        when(stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(blankAddress));
        assertEquals(1, stationService.search(0.0, 0.0, 5.0, "  ").size());
    }

    @Test
    void searchesPostalCodeWithoutMaskAndSkipsGeocoding() {
        Station match = station("Posto CEP", decimal("-23.55"), decimal("-46.63"));
        when(stationRepository.searchByPostalCode(StationStatus.ACTIVE, "06132000"))
                .thenReturn(List.of(match));

        var results = stationService.search(null, null, null, " 06132-000 ");

        assertEquals("Posto CEP", results.getFirst().corporateName());
        verify(geocodingService, never()).geocode(any());
        verify(stationRepository).searchByPostalCode(StationStatus.ACTIVE, "06132000");
    }

    @Test
    void hidesInactiveStationsAndDeactivatesWithoutDeleting() {
        Station station = station("Station", decimal("0"), decimal("0"));
        when(stationRepository.findByIdAndStatus(STATION_ID, StationStatus.ACTIVE))
                .thenReturn(Optional.of(station), Optional.empty());
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(station));

        assertEquals("Station", stationService.findActiveById(STATION_ID).corporateName());
        stationService.deactivate(STATION_ID);
        assertEquals(StationStatus.INACTIVE, station.getStatus());
        verify(stationRepository).save(station);
        assertThrows(ResourceNotFoundException.class, () -> stationService.findActiveById(STATION_ID));

        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> stationService.deactivate(STATION_ID));
    }

    @Test
    void updatesFieldsAndRejectsEmptyRequiredValuesAndDuplicateCnpj() {
        Station station = station("Original", decimal("0"), decimal("0"));
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(station));
        when(stationRepository.existsByCnpj("22345678000195")).thenReturn(false);

        UpdateStationRequestDTO request = new UpdateStationRequestDTO(
                "22345678000195", "Updated", "Trade", "Brand", "New street",
                "20", "District", "New city", "rj", "20000-000",
                decimal("-22.9"), decimal("-43.2"));
        var response = stationService.update(STATION_ID, request);
        assertEquals("Updated", response.corporateName());
        assertEquals("RJ", response.state());
        assertEquals(decimal("-22.9"), response.latitude());
        assertEquals("New street, 20, District", response.address());

        UpdateStationRequestDTO emptyPatch = new UpdateStationRequestDTO(
                null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals("Updated", stationService.update(STATION_ID, emptyPatch).corporateName());
        UpdateStationRequestDTO sameCnpj = new UpdateStationRequestDTO(
                station.getCnpj(), null, null, null, null, null, null, null, null, null, null, null);
        assertEquals("Updated", stationService.update(STATION_ID, sameCnpj).corporateName());
        UpdateStationRequestDTO blankCnpj = new UpdateStationRequestDTO(
                " ", null, null, null, null, null, null, null, null, null, null, null);
        assertEquals("Updated", stationService.update(STATION_ID, blankCnpj).corporateName());

        UpdateStationRequestDTO blankName = new UpdateStationRequestDTO(
                null, "  ", null, null, null, null, null, null, null, null, null, null);
        assertThrows(BusinessException.class, () -> stationService.update(STATION_ID, blankName));

        when(stationRepository.existsByCnpj("99999999000199")).thenReturn(true);
        UpdateStationRequestDTO duplicateCnpj = new UpdateStationRequestDTO(
                "99999999000199", null, null, null, null, null, null, null, null, null, null, null);
        assertThrows(DuplicateResourceException.class,
                () -> stationService.update(STATION_ID, duplicateCnpj));

        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> stationService.update(STATION_ID,
                        new UpdateStationRequestDTO(null, null, null, null, null, null,
                                null, null, null, null, null, null)));
    }

    private CreateStationRequestDTO createRequest(
            String cnpj, String city, BigDecimal latitude, BigDecimal longitude) {
        return new CreateStationRequestDTO(
                cnpj, "Corporate", "Trade", "Brand", "Rua A", "10", "Centro",
                city, "sp", "01000-000", latitude, longitude);
    }

    private Station station(String name, BigDecimal latitude, BigDecimal longitude) {
        Station station = new Station(
                "12345678000195", name, null, null, null, null, null,
                "City", "SP", null, latitude, longitude);
        station.setId(STATION_ID);
        return station;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}

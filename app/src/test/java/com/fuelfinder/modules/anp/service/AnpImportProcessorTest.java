package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.fuel.repository.FuelTypeRepository;
import com.fuelfinder.modules.price.entity.DataSource;
import com.fuelfinder.modules.price.entity.FuelPrice;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.station.service.GeoapifyGeocodingService;
import com.fuelfinder.modules.station.service.GeoapifyRateLimitException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnpImportProcessorTest {

    @Mock
    private StationRepository stationRepository;
    @Mock
    private FuelPriceRepository fuelPriceRepository;
    @Mock
    private FuelTypeRepository fuelTypeRepository;
    @Mock
    private GeoapifyGeocodingService geocodingService;
    @Mock
    private EntityManager entityManager;

    private AnpImportProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AnpImportProcessor(
                stationRepository,
                fuelPriceRepository,
                fuelTypeRepository,
                geocodingService,
                entityManager);
        lenient().when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> {
            Station station = invocation.getArgument(0);
            if (station.getId() == null) {
                station.setId(UUID.randomUUID());
            }
            return station;
        });
        lenient().when(stationRepository.findByCnpjIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    void importsAllMappedProductsAndLeavesStationsWithoutGeoapifyCoordinates() {
        when(geocodingService.isConfigured()).thenReturn(false);
        stubFuelType("GASOLINE_REGULAR", "R$/litro", true);
        stubFuelType("GASOLINE_PREMIUM", "R$/litro", true);
        stubFuelType("ETHANOL", "R$/litro", true);
        stubFuelType("DIESEL_S10", "R$/litro", true);
        stubFuelType("DIESEL_S500", "R$/litro", true);
        stubFuelType("CNG", "R$/m³", true);

        AnpImportResult result = processor.process(csv(
                row("12345678000195", "GASOLINA COMUM", "5,899", "R$/L", "29/09/2026"),
                row("12345678000196", "GASOLINA ADITIVADA", "6.10", "R$/litro", "2026-09-29"),
                row("12345678000197", "ETANOL HIDRATADO", "4,199", "R$/L", "29/09/2026"),
                row("12345678000198", "DIESEL S10", "6,50", "R$/LT", "29/09/2026"),
                row("12345678000199", "DIESEL S500", "6,30", "R$/L", "29/09/2026"),
                row("12345678000200", "GNV", "4,00", "R$/m³", "29/09/2026"),
                row("12345678000201", "GASOLINA", "7,97", "R$ / litro", "02/01/2026"),
                row("12345678000202", "DIESEL", "8,15", "R$ / litro", "02/01/2026")));

        assertEquals(8, result.totalRecordsRead());
        assertEquals(8, result.totalRecordsImported());
        assertEquals(8, result.errors().size());
        verify(fuelPriceRepository, times(8)).save(any(FuelPrice.class));
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void normalizesCnpjUpdatesChangedPricesAndSkipsUnchangedReimports() {
        stubFuelType("ETHANOL", "R$/litro", true);
        Station existingStation = station("12345678000195");
        existingStation.setLatitude(BigDecimal.ZERO);
        existingStation.setLongitude(BigDecimal.ZERO);
        FuelPrice existingPrice = new FuelPrice(
                existingStation,
                fuelType("ETHANOL", "R$/litro", true),
                new BigDecimal("4.00"),
                LocalDate.of(2026, 9, 29),
                DataSource.MANUAL_ADMIN);
        when(stationRepository.findByCnpjIn(Set.of("12345678000195")))
                .thenReturn(List.of(existingStation));
        when(fuelPriceRepository.findByCollectionDateBetweenWithRelations(
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 29)))
                .thenReturn(List.of(existingPrice));
        AnpCsvRow row = row("12.345.678/0001-95", "ETANOL", "4,199", "R$/L", "29/09/2026");

        AnpImportResult changed = processor.process(csv(row));
        AnpImportResult unchanged = processor.process(csv(row));

        assertEquals(1, changed.totalRecordsImported());
        assertEquals(0, unchanged.totalRecordsImported());
        assertEquals(new BigDecimal("4.199"), existingPrice.getSaleValue());
        assertEquals(DataSource.ANP_IMPORT, existingPrice.getDataSource());
        verify(fuelPriceRepository, times(1)).save(existingPrice);
    }

    @Test
    void importsGeocodedCoordinatesAndCachesResultsByCnpj() {
        when(geocodingService.isConfigured()).thenReturn(true);
        when(geocodingService.geocode("Rua 10 10, Centro, 01000-000, Sao Paulo, SP, Brasil"))
                .thenReturn(Optional.of(
                        new GeoapifyGeocodingService.GeoPoint(-23.5, -46.6)));
        stubFuelType("ETHANOL", "R$/litro", true);
        stubFuelType("GASOLINE_REGULAR", "R$/litro", true);

        AnpImportResult result = processor.process(csv(
                row("12345678000195", "ETANOL", "4,199", "R$/L", "29/09/2026"),
                row("12345678000195", "GASOLINA COMUM", "5,899", "R$/L", "29/09/2026")));

        assertEquals(2, result.totalRecordsImported());
        assertEquals(List.of(), result.errors());
        verify(geocodingService, times(1))
                .geocode("Rua 10 10, Centro, 01000-000, Sao Paulo, SP, Brasil");
        verify(stationRepository, times(1)).save(any(Station.class));
        verify(stationRepository, never()).findByCnpj(anyString());
        verify(fuelTypeRepository, times(1)).findByCode("ETHANOL");
        verify(fuelTypeRepository, times(1)).findByCode("GASOLINE_REGULAR");
    }

    @Test
    void recordsMissingUnmatchedFailedAndInvalidGeoapifyResultsAsPartial() {
        when(geocodingService.isConfigured()).thenReturn(true);
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.empty())
                .thenThrow(new ExternalServiceException("service failed", new RuntimeException()))
                .thenReturn(Optional.of(new GeoapifyGeocodingService.GeoPoint(95, 200)));
        stubFuelType("ETHANOL", "R$/litro", true);

        AnpImportResult result = processor.process(csv(
                row("12345678000195", "ETANOL", "4,199", "R$/L", "29/09/2026"),
                row("12345678000196", "ETANOL", "4,199", "R$/L", "29/09/2026"),
                row("12345678000197", "ETANOL", "4,199", "R$/L", "29/09/2026")));

        assertEquals(3, result.totalRecordsImported());
        assertEquals(3, result.errors().size());
        assertEquals(3L, result.errors().stream()
                .filter(error -> error.contains("Geoapify")).count());
    }

    @Test
    void skipsInvalidRecordsWithoutLosingOtherValidRows() {
        stubFuelType("GASOLINE_REGULAR", "R$/litro", true);
        when(fuelTypeRepository.findByCode("DIESEL_S10")).thenReturn(Optional.empty());
        when(fuelTypeRepository.findByCode("DIESEL_S500"))
                .thenReturn(Optional.of(fuelType("DIESEL_S500", "R$/litro", false)));
        when(geocodingService.isConfigured()).thenReturn(false);

        AnpImportResult result = processor.process(csv(
                row("invalid", "ETANOL", "4.199", "R$/L", "29/09/2026"),
                row("12345678000195", "ETANOL DESCONHECIDO", "4.199", "R$/L", "29/09/2026"),
                row("12345678000196", "ETANOL", "abc", "R$/L", "29/09/2026"),
                row("12345678000197", "ETANOL", "0", "R$/L", "29/09/2026"),
                row("12345678000198", "ETANOL", "4.1234", "R$/L", "29/09/2026"),
                row("12345678000199", "ETANOL", "4.199", "R$/m³", "29/09/2026"),
                row("12345678000200", "ETANOL", "4.199", "R$/L", "31/02/2026"),
                row("12345678000201", "ETANOL", "4.199", "R$/L", "29/09/2026", "X"),
                row("12345678000202", "DIESEL S10", "6.50", "R$/L", "29/09/2026"),
                row("12345678000203", "DIESEL S500", "6.50", "R$/L", "29/09/2026"),
                row("12345678000204", "ETANOL", "4.199", "R$/L", "29/09/2026", "S"),
                row("12345678000205", "GASOLINA COMUM", "5.899", "R$/L", "29/09/2026")));

        assertEquals(10, result.totalRecordsRead());
        assertEquals(1, result.totalRecordsImported());
        assertEquals(14, result.errors().size());
    }

    @Test
    void reportsMissingCnpjRequiredNameAndPriceUnit() {
        Map<String, String> missingCnpj = new HashMap<>(validRecordValues());
        missingCnpj.remove("cnpj da revenda");
        Map<String, String> missingName = new HashMap<>(validRecordValues());
        missingName.remove("revenda");
        Map<String, String> missingUnit = new HashMap<>(validRecordValues());
        missingUnit.remove("unidade de medida");

        AnpImportResult result = processor.process(csv(
                new AnpCsvRow(2, missingCnpj),
                new AnpCsvRow(3, missingName),
                new AnpCsvRow(4, missingUnit)));

        assertEquals(0, result.totalRecordsImported());
        assertEquals(3, result.errors().size());
    }

    @Test
    void boundsPersistenceContextWhileProcessingLargeImports() {
        stubFuelType("ETHANOL", "R$/litro", true);
        when(geocodingService.isConfigured()).thenReturn(false);
        List<AnpCsvRow> rows = java.util.stream.IntStream.range(0, 201)
                .mapToObj(index -> row(
                        "12345678000195", "ETANOL", "4,199", "R$/L", "29/09/2026"))
                .toList();

        AnpImportResult result = processor.process(new ParsedAnpCsv(201, rows, List.of()));

        assertEquals(201, result.totalRecordsImported());
        verify(entityManager, times(4)).flush();
        verify(entityManager, times(3)).clear();
    }

    @Test
    void skipsRowsWhenCatalogUnitIsInvalidAndReusesExistingGeocodedStations() {
        stubFuelType("ETHANOL", "R$/m³", true);
        Station station = station("12345678000196");
        station.setLatitude(BigDecimal.ZERO);
        station.setLongitude(BigDecimal.ZERO);
        when(stationRepository.findByCnpjIn(Set.of("12345678000195", "12345678000196")))
                .thenReturn(List.of(station));
        when(geocodingService.isConfigured()).thenReturn(true);
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(new GeoapifyGeocodingService.GeoPoint(-23.5, -46.6)));
        stubFuelType("GASOLINE_REGULAR", "R$/litro", true);

        AnpImportResult result = processor.process(csv(
                row("12345678000195", "ETANOL", "4.199", "R$/L", "29/09/2026"),
                row("12345678000196", "GASOLINA COMUM", "5.899", "R$/L", "29/09/2026")));

        assertEquals(1, result.totalRecordsImported());
        assertEquals(1, result.errors().size());
        verify(geocodingService, times(1)).geocode(anyString());
    }

    @Test
    void filtersNonSpRowsAndGeocodesOnlyOnceForEachDistinctCnpj() {
        when(geocodingService.isConfigured()).thenReturn(true);
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(new GeoapifyGeocodingService.GeoPoint(-23.5, -46.6)));
        stubFuelType("ETHANOL", "R$/litro", true);

        AnpImportResult result = processor.process(csv(
                row("12.345.678/0001-95", "ETANOL", "4,199", "R$/L", "29/09/2026"),
                row("12345678000195", "ETANOL", "4,299", "R$/L", "30/09/2026"),
                row("12345678000196", "ETANOL", "4,399", "R$/L", "29/09/2026", "RJ")));

        assertEquals(2, result.totalRecordsRead());
        assertEquals(2, result.totalRecordsImported());
        assertEquals(List.of(), result.errors());
        verify(geocodingService, times(1))
                .geocode("Rua 10 10, Centro, 01000-000, Sao Paulo, SP, Brasil");
        verify(stationRepository, times(1)).save(any(Station.class));
        verify(fuelPriceRepository, times(2)).save(any(FuelPrice.class));
        verify(stationRepository).findByCnpjIn(Set.of("12345678000195"));
    }

    @Test
    void reusesPersistedCoordinatesAndDoesNotCallGeocoderAgain() {
        Station existingStation = station("12345678000195");
        existingStation.setLatitude(new BigDecimal("-23.5"));
        existingStation.setLongitude(new BigDecimal("-46.6"));
        when(stationRepository.findByCnpjIn(Set.of("12345678000195")))
                .thenReturn(List.of(existingStation));
        stubFuelType("ETHANOL", "R$/litro", true);

        AnpImportResult result = processor.process(csv(
                row("12345678000195", "ETANOL", "4,199", "R$/L", "29/09/2026")));

        assertEquals(1, result.totalRecordsImported());
        assertEquals(List.of(), result.errors());
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void preservesFuelPricesWhenAddressIsIncompleteAndSkipsGeocoding() {
        when(geocodingService.isConfigured()).thenReturn(true);
        stubFuelType("ETHANOL", "R$/litro", true);
        Map<String, String> values = new HashMap<>(row(
                "12345678000195", "ETANOL", "4,199", "R$/L", "29/09/2026").values());
        values.put("nome da rua", "");
        values.put("numero rua", "");
        values.put("bairro", "");
        values.put("cep", "");

        AnpImportResult result = processor.process(csv(new AnpCsvRow(2, Map.copyOf(values))));

        assertEquals(1, result.totalRecordsImported());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Endereço incompleto"));
        verify(geocodingService, never()).geocode(anyString());
        verify(fuelPriceRepository).save(any(FuelPrice.class));
    }

    @Test
    void stopsGeocodingAfterRateLimitButContinuesImportingFuelPrices() {
        when(geocodingService.isConfigured()).thenReturn(true);
        when(geocodingService.geocode(anyString()))
                .thenThrow(new GeoapifyRateLimitException(new RuntimeException()));
        stubFuelType("ETHANOL", "R$/litro", true);

        AnpImportResult result = processor.process(csv(
                row("12345678000195", "ETANOL", "4,199", "R$/L", "29/09/2026"),
                row("12345678000196", "ETANOL", "4,299", "R$/L", "29/09/2026")));

        assertEquals(2, result.totalRecordsImported());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("limite")));
        verify(geocodingService, times(1)).geocode(anyString());
        verify(fuelPriceRepository, times(2)).save(any(FuelPrice.class));
    }

    private void stubFuelType(String code, String unit, boolean active) {
        when(fuelTypeRepository.findByCode(code))
                .thenReturn(Optional.of(fuelType(code, unit, active)));
    }

    private FuelType fuelType(String code, String unit, boolean active) {
        FuelType fuelType = new FuelType();
        fuelType.setId(UUID.nameUUIDFromBytes(code.getBytes(StandardCharsets.UTF_8)));
        fuelType.setCode(code);
        fuelType.setName(code);
        fuelType.setUnitOfMeasure(unit);
        fuelType.setActive(active);
        return fuelType;
    }

    private Station station(String cnpj) {
        Station station = new Station(
                cnpj, "Corporate", "Trade", "Brand",
                "Rua 10", "10", "Centro", "Sao Paulo", "SP", "01000-000",
                null, null);
        station.setId(UUID.nameUUIDFromBytes(cnpj.getBytes(StandardCharsets.UTF_8)));
        return station;
    }

    private ParsedAnpCsv csv(AnpCsvRow... rows) {
        List<AnpCsvRow> numberedRows = java.util.stream.IntStream.range(0, rows.length)
                .mapToObj(index -> new AnpCsvRow(index + 2, rows[index].values()))
                .toList();
        return new ParsedAnpCsv(rows.length, numberedRows, List.of());
    }

    private AnpCsvRow row(
            String cnpj,
            String product,
            String price,
            String unit,
            String date) {
        return row(cnpj, product, price, unit, date, "SP");
    }

    private Map<String, String> validRecordValues() {
        return new HashMap<>(row(
                "12345678000195", "ETANOL", "4,199", "R$/L", "29/09/2026").values());
    }

    private AnpCsvRow row(
            String cnpj,
            String product,
            String price,
            String unit,
            String date,
            String state) {
        Map<String, String> values = new HashMap<>();
        values.put("cnpj da revenda", cnpj);
        values.put("revenda", "Posto Teste");
        values.put("municipio", "Sao Paulo");
        values.put("estado sigla", state);
        values.put("produto", product);
        values.put("data da coleta", date);
        values.put("valor de venda", price);
        values.put("unidade de medida", unit);
        values.put("nome da rua", "Rua 10");
        values.put("numero rua", "10");
        values.put("bairro", "Centro");
        values.put("bandeira", "Marca");
        values.put("cep", "01000-000");
        return new AnpCsvRow(2, Map.copyOf(values));
    }
}

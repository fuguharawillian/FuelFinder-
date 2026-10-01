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
import com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted;
import com.fuelfinder.modules.vehicle.entity.VolumeUnit;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AnpImportProcessor {

    private static final int PERSISTENCE_CONTEXT_BATCH_SIZE = 100;
    private static final DateTimeFormatter BRAZILIAN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern NUMERIC_CNPJ = Pattern.compile("[0-9]{14}");
    private final StationRepository stationRepository;
    private final FuelPriceRepository fuelPriceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final GeoapifyGeocodingService geocodingService;
    private final EntityManager entityManager;

    public AnpImportProcessor(
            StationRepository stationRepository,
            FuelPriceRepository fuelPriceRepository,
            FuelTypeRepository fuelTypeRepository,
            GeoapifyGeocodingService geocodingService,
            EntityManager entityManager) {
        this.stationRepository = stationRepository;
        this.fuelPriceRepository = fuelPriceRepository;
        this.fuelTypeRepository = fuelTypeRepository;
        this.geocodingService = geocodingService;
        this.entityManager = entityManager;
    }

    @Transactional
    public AnpImportResult process(ParsedAnpCsv csv) {
        Set<String> errors = new LinkedHashSet<>(csv.errors());
        Map<String, GeocodingResolution> geocodingCache = new HashMap<>();
        Map<String, FuelType> fuelTypeCache = new HashMap<>();
        Map<String, Station> stationCache = new HashMap<>();
        Map<PriceKey, FuelPrice> existingPrices = loadExistingPrices(csv.records());
        int importedRecords = 0;
        int recordsSinceFlush = 0;
        for (AnpCsvRow row : csv.records()) {
            try {
                AnpPriceRecord record = parseRecord(row);
                FuelType fuelType = fuelTypeCache.computeIfAbsent(
                        record.fuelCode(), this::findActiveFuelType);
                Station station = stationCache.computeIfAbsent(
                        record.cnpj(),
                        cnpj -> stationRepository.findByCnpj(cnpj)
                                .orElseGet(() -> createStation(record)));
                applyStationDetails(station, record);
                geocodeIfNeeded(station, record, geocodingCache, errors);
                station = stationRepository.save(station);
                stationCache.put(record.cnpj(), station);
                if (savePriceIfChanged(station, fuelType, record, existingPrices)) {
                    importedRecords++;
                }
            } catch (AnpRecordException exception) {
                errors.add("Linha " + row.lineNumber() + ": " + exception.getMessage());
            }
            if (++recordsSinceFlush == PERSISTENCE_CONTEXT_BATCH_SIZE) {
                entityManager.flush();
                entityManager.clear();
                recordsSinceFlush = 0;
            }
        }
        entityManager.flush();
        return new AnpImportResult(
                csv.totalRecordsRead(),
                importedRecords,
                List.copyOf(errors));
    }

    private AnpPriceRecord parseRecord(AnpCsvRow row) {
        Map<String, String> values = row.values();
        String cnpj = normalizeCnpj(values.get("cnpj da revenda"));
        String corporateName = requiredText(values.get("revenda"), "revenda");
        String city = requiredText(values.get("municipio"), "município");
        String state = requiredText(values.get("estado sigla"), "estado").toUpperCase(Locale.ROOT);
        if (!state.matches("[A-Z]{2}")) {
            throw new AnpRecordException("a sigla do estado deve conter duas letras.");
        }
        String fuelCode = mapFuelCode(values.get("produto"));
        LocalDate collectionDate = parseDate(values.get("data da coleta"));
        BigDecimal saleValue = parsePrice(values.get("valor de venda"));
        String priceUnit = normalizeUnit(values.get("unidade de medida"));
        validatePriceUnit(fuelCode, priceUnit);
        return new AnpPriceRecord(
                cnpj,
                corporateName,
                trimToNull(values.get("bandeira")),
                trimToNull(values.get("nome da rua")),
                trimToNull(values.get("numero rua")),
                trimToNull(values.get("bairro")),
                trimToNull(values.get("cep")),
                city,
                state,
                fuelCode,
                collectionDate,
                saleValue);
    }

    private String normalizeCnpj(String rawCnpj) {
        if (rawCnpj == null) {
            throw new AnpRecordException("CNPJ da revenda obrigatório.");
        }
        String normalized = rawCnpj.replaceAll("[^0-9]", "");
        if (!NUMERIC_CNPJ.matcher(normalized).matches()) {
            throw new AnpRecordException("CNPJ da revenda deve conter 14 dígitos.");
        }
        return normalized;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AnpRecordException("campo " + fieldName + " obrigatório.");
        }
        return value.trim();
    }

    private String mapFuelCode(String product) {
        String normalized = normalizeText(product).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GASOLINA", "GASOLINA COMUM" -> "GASOLINE_REGULAR";
            case "GASOLINA ADITIVADA", "GASOLINA PREMIUM" -> "GASOLINE_PREMIUM";
            case "ETANOL", "ETANOL HIDRATADO", "ETANOL HIDRATADO COMUM" -> "ETHANOL";
            case "DIESEL S10" -> "DIESEL_S10";
            case "DIESEL", "DIESEL S500" -> "DIESEL_S500";
            case "GNV", "GAS NATURAL VEICULAR" -> "CNG";
            default -> throw new AnpRecordException(
                    "produto ANP não reconhecido: " + safeValue(product) + ".");
        };
    }

    private LocalDate parseDate(String rawDate) {
        String value = requiredText(rawDate, "data da coleta");
        try {
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                return LocalDate.parse(value);
            }
            return LocalDate.parse(value, BRAZILIAN_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new AnpRecordException("data da coleta inválida.");
        }
    }

    private BigDecimal parsePrice(String rawPrice) {
        String value = requiredText(rawPrice, "valor de venda")
                .replace("R$", "")
                .replace("\u00a0", "")
                .replace(" ", "");
        if (value.contains(",")) {
            value = value.replace(".", "").replace(',', '.');
        }
        try {
            BigDecimal price = new BigDecimal(value);
            if (price.signum() <= 0
                    || price.stripTrailingZeros().scale() > 3
                    || price.precision() - price.scale() > 5) {
                throw new AnpRecordException("valor de venda deve ser positivo e ter até três casas decimais.");
            }
            return price;
        } catch (NumberFormatException exception) {
            throw new AnpRecordException("valor de venda inválido.");
        }
    }

    private String normalizeUnit(String rawUnit) {
        if (rawUnit == null) {
            throw new AnpRecordException("unidade de medida obrigatória.");
        }
        return normalizeText(rawUnit)
                .toUpperCase(Locale.ROOT)
                .replace("³", "3")
                .replaceAll("\\s", "");
    }

    private void validatePriceUnit(String fuelCode, String unit) {
        boolean cng = "CNG".equals(fuelCode);
        Set<String> acceptedUnits = cng
                ? Set.of("R$/M3", "R$/M^3")
                : Set.of("R$/L", "R$/LITRO", "R$/LT");
        if (!acceptedUnits.contains(unit)) {
            throw new AnpRecordException(
                    "unidade de medida incompatível com o produto " + fuelCode + ".");
        }
    }

    private FuelType findActiveFuelType(String code) {
        FuelType fuelType = fuelTypeRepository.findByCode(code)
                .orElseThrow(() -> new AnpRecordException(
                        "tipo de combustível não cadastrado: " + code + "."));
        if (!Boolean.TRUE.equals(fuelType.getActive())) {
            throw new AnpRecordException("tipo de combustível inativo: " + code + ".");
        }
        String expectedUnit = "CNG".equals(code) ? "R$/m³" : "R$/litro";
        if (!expectedUnit.equals(fuelType.getUnitOfMeasure())) {
            throw new AnpRecordException(
                    "unidade do catálogo incompatível para o combustível " + code + ".");
        }
        return fuelType;
    }

    private Station createStation(AnpPriceRecord record) {
        return new Station(
                record.cnpj(),
                record.corporateName(),
                record.corporateName(),
                record.brand(),
                record.street(),
                record.number(),
                record.neighborhood(),
                record.city(),
                record.state(),
                record.postalCode(),
                null,
                null);
    }

    private void applyStationDetails(Station station, AnpPriceRecord record) {
        station.setCorporateName(record.corporateName());
        station.setTradeName(record.corporateName());
        station.setBrand(record.brand());
        station.setStreet(record.street());
        station.setNumber(record.number());
        station.setNeighborhood(record.neighborhood());
        station.setCity(record.city());
        station.setState(record.state());
        station.setPostalCode(record.postalCode());
    }

    private void geocodeIfNeeded(
            Station station,
            AnpPriceRecord record,
            Map<String, GeocodingResolution> cache,
            Set<String> errors) {
        if (station.getLatitude() != null && station.getLongitude() != null) {
            return;
        }
        GeocodingResolution resolution = cache.computeIfAbsent(
                record.cnpj(), ignored -> geocode(record));
        if (resolution.point() != null) {
            station.setLatitude(BigDecimal.valueOf(resolution.point().latitude()));
            station.setLongitude(BigDecimal.valueOf(resolution.point().longitude()));
        } else {
            errors.add(resolution.error());
        }
    }

    private GeocodingResolution geocode(AnpPriceRecord record) {
        if (!geocodingService.isConfigured()) {
            return new GeocodingResolution(
                    null,
                    "Geoapify desabilitada (GEOAPIFY_API_KEY ausente) para o CNPJ "
                            + record.cnpj() + ".");
        }
        String address = buildAddress(record);
        try {
            Optional<GeoapifyGeocodingService.GeoPoint> point = geocodingService.geocode(address);
            if (point.isEmpty()) {
                return new GeocodingResolution(
                        null, "Geoapify não encontrou coordenadas para o CNPJ "
                                + record.cnpj() + ".");
            }
            GeoapifyGeocodingService.GeoPoint geoPoint = point.get();
            if (!Double.isFinite(geoPoint.latitude())
                    || geoPoint.latitude() < -90 || geoPoint.latitude() > 90
                    || !Double.isFinite(geoPoint.longitude())
                    || geoPoint.longitude() < -180 || geoPoint.longitude() > 180) {
                return new GeocodingResolution(
                        null, "Geoapify retornou coordenadas inválidas para o CNPJ "
                                + record.cnpj() + ".");
            }
            return new GeocodingResolution(geoPoint, null);
        } catch (ExternalServiceException exception) {
            return new GeocodingResolution(
                    null, "Falha na geocodificação Geoapify para o CNPJ "
                            + record.cnpj() + ".");
        }
    }

    private String buildAddress(AnpPriceRecord record) {
        List<String> parts = new ArrayList<>();
        String street = record.street();
        if (street != null && record.number() != null) {
            street += " " + record.number();
        }
        addIfPresent(parts, street);
        addIfPresent(parts, record.neighborhood());
        addIfPresent(parts, record.city());
        addIfPresent(parts, record.state());
        parts.add("Brasil");
        return String.join(", ", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private boolean savePriceIfChanged(
            Station station,
            FuelType fuelType,
            AnpPriceRecord record,
            Map<PriceKey, FuelPrice> existingPrices) {
        PriceKey key = new PriceKey(record.cnpj(), record.fuelCode(), record.collectionDate());
        FuelPrice existing = existingPrices.get(key);
        if (existing != null) {
            if (existing.getSaleValue().compareTo(record.saleValue()) == 0) {
                return false;
            }
            existing.setSaleValue(record.saleValue());
            existing.setDataSource(DataSource.ANP_IMPORT);
            existingPrices.put(key, fuelPriceRepository.save(existing));
            return true;
        }
        FuelPrice price = fuelPriceRepository.save(new FuelPrice(
                station,
                fuelType,
                record.saleValue(),
                record.collectionDate(),
                DataSource.ANP_IMPORT));
        existingPrices.put(key, price);
        return true;
    }

    private Map<PriceKey, FuelPrice> loadExistingPrices(List<AnpCsvRow> rows) {
        LocalDate startDate = null;
        LocalDate endDate = null;
        for (AnpCsvRow row : rows) {
            try {
                LocalDate date = parseDate(row.values().get("data da coleta"));
                if (startDate == null || date.isBefore(startDate)) {
                    startDate = date;
                }
                if (endDate == null || date.isAfter(endDate)) {
                    endDate = date;
                }
            } catch (AnpRecordException ignored) {
                // The main pass reports invalid records with their physical line numbers.
            }
        }
        if (startDate == null) {
            return new HashMap<>();
        }
        Map<PriceKey, FuelPrice> prices = new HashMap<>();
        for (FuelPrice price : fuelPriceRepository
                .findByCollectionDateBetweenWithRelations(startDate, endDate)) {
            prices.put(
                    new PriceKey(
                            price.getStation().getCnpj(),
                            price.getFuelType().getCode(),
                            price.getCollectionDate()),
                    price);
        }
        return prices;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeText(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim();
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "(vazio)" : value.trim();
    }

    private record AnpPriceRecord(
            String cnpj,
            String corporateName,
            String brand,
            String street,
            String number,
            String neighborhood,
            String postalCode,
            String city,
            String state,
            String fuelCode,
            LocalDate collectionDate,
            BigDecimal saleValue) {
    }

    private record GeocodingResolution(
            GeoapifyGeocodingService.GeoPoint point,
            String error) {
    }

    private record PriceKey(String cnpj, String fuelCode, LocalDate collectionDate) {
    }
}

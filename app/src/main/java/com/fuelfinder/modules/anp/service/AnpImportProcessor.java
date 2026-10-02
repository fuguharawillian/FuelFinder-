package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.fuel.repository.FuelTypeRepository;
import com.fuelfinder.modules.price.entity.DataSource;
import com.fuelfinder.modules.price.entity.FuelPrice;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.BiConsumer;

@Service
public class AnpImportProcessor {

    private static final int PERSISTENCE_CONTEXT_BATCH_SIZE = 100;
    private static final DateTimeFormatter BRAZILIAN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern NUMERIC_CNPJ = Pattern.compile("[0-9]{14}");
    private final StationRepository stationRepository;
    private final FuelPriceRepository fuelPriceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final EntityManager entityManager;

    public AnpImportProcessor(
            StationRepository stationRepository,
            FuelPriceRepository fuelPriceRepository,
            FuelTypeRepository fuelTypeRepository,
            EntityManager entityManager) {
        this.stationRepository = stationRepository;
        this.fuelPriceRepository = fuelPriceRepository;
        this.fuelTypeRepository = fuelTypeRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AnpImportResult process(ParsedAnpCsv csv) {
        return process(csv, (processed, total) -> { });
    }

    @Transactional
    public AnpImportResult process(
            ParsedAnpCsv csv,
            BiConsumer<Integer, Integer> progress) {
        Set<String> errors = new LinkedHashSet<>(csv.errors());
        StateFilteredRows filteredRows = filterSpRows(csv.records(), errors);
        List<AnpCsvRow> spRows = filteredRows.rows();
        Map<String, FuelType> fuelTypeCache = new HashMap<>();
        Map<String, StationRecord> stationRecords =
                collectStationRecords(spRows, errors, fuelTypeCache);
        StationPreparation stationPreparation = prepareStations(stationRecords, errors);
        Map<String, Station> stations = stationPreparation.stations();
        entityManager.flush();
        entityManager.clear();
        fuelTypeCache.clear();
        Map<PriceKey, FuelPrice> existingPrices = loadExistingPrices(spRows);
        Map<PriceKey, BigDecimal> csvPrices = new HashMap<>();
        int importedRecords = 0;
        int pricesAssociated = 0;
        int recordsSinceFlush = 0;
        int processedRows = 0;
        for (AnpCsvRow row : spRows) {
            try {
                AnpPriceRecord record = parseRecord(row);
                FuelType fuelType = fuelTypeCache.computeIfAbsent(
                        record.fuelCode(), this::findActiveFuelType);
                Station station = stations.get(record.cnpj());
                if (station == null) {
                    throw new AnpRecordException(
                            "cadastro do posto não pôde ser preparado para o CNPJ "
                                    + record.cnpj() + ".");
                }
                pricesAssociated++;
                PriceKey key = new PriceKey(
                        record.cnpj(), record.fuelCode(), record.collectionDate());
                BigDecimal previousCsvPrice = csvPrices.putIfAbsent(key, record.saleValue());
                if (previousCsvPrice != null
                        && previousCsvPrice.compareTo(record.saleValue()) != 0) {
                    errors.add("Combinação duplicada CNPJ + combustível + data com valores divergentes "
                            + "na linha " + row.lineNumber()
                            + "; prevaleceu a última linha válida do arquivo.");
                }
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
            processedRows++;
            if (processedRows % 1_000 == 0 || processedRows == spRows.size()) {
                progress.accept(processedRows, spRows.size());
            }
        }
        entityManager.flush();
        return new AnpImportResult(
                csv.totalRecordsRead(),
                importedRecords,
                pricesAssociated,
                filteredRows.otherStateRows(),
                countInvalidRows(errors),
                stationPreparation.created(),
                stationPreparation.updated(),
                stationPreparation.withoutCoordinates(),
                pricesAssociated,
                Set.copyOf(stationRecords.keySet()),
                List.copyOf(errors));
    }

    private StateFilteredRows filterSpRows(List<AnpCsvRow> rows, Set<String> errors) {
        List<AnpCsvRow> spRows = new ArrayList<>();
        int otherStateRows = 0;
        for (AnpCsvRow row : rows) {
            String state = trimToNull(row.values().get("estado sigla"));
            if (state == null) {
                errors.add("Linha " + row.lineNumber()
                        + ": estado sigla ausente; linha ignorada.");
            } else if ("SP".equalsIgnoreCase(state)) {
                spRows.add(row);
            } else if (!state.matches("[A-Za-z]{2}")) {
                errors.add("Linha " + row.lineNumber()
                        + ": estado sigla inválido; linha ignorada.");
            } else {
                otherStateRows++;
            }
        }
        return new StateFilteredRows(List.copyOf(spRows), otherStateRows);
    }

    private Map<String, StationRecord> collectStationRecords(
            List<AnpCsvRow> rows,
            Set<String> errors,
            Map<String, FuelType> fuelTypeCache) {
        Map<String, StationRecord> stationRecords = new java.util.LinkedHashMap<>();
        for (AnpCsvRow row : rows) {
            try {
                AnpPriceRecord priceRecord = parseRecord(row);
                fuelTypeCache.computeIfAbsent(priceRecord.fuelCode(), this::findActiveFuelType);
                StationRecord candidate = parseStationRecord(row);
                stationRecords.merge(
                        candidate.cnpj(),
                        candidate,
                        (current, next) -> {
                            if (!sameStationDetails(current, next)) {
                                errors.add("CNPJ " + candidate.cnpj()
                                        + " possui dados cadastrais divergentes em linhas do CSV; "
                                        + "foi escolhido o registro com endereço mais completo"
                                        + " (empate: primeira ocorrência).");
                            }
                            return addressCompleteness(next) > addressCompleteness(current)
                                    ? next : current;
                        });
            } catch (AnpRecordException exception) {
                errors.add("Linha " + row.lineNumber() + ": " + exception.getMessage());
            }
        }
        return stationRecords;
    }

    private StationPreparation prepareStations(
            Map<String, StationRecord> stationRecords,
            Set<String> errors) {
        if (stationRecords.isEmpty()) {
            return new StationPreparation(Map.of(), 0, 0, 0);
        }
        Map<String, Station> stations = new HashMap<>();
        for (Station station : stationRepository.findByCnpjIn(stationRecords.keySet())) {
            stations.put(station.getCnpj(), station);
        }

        int created = 0;
        int updated = 0;
        int withoutCoordinates = 0;
        for (StationRecord record : stationRecords.values()) {
            Station station = stations.get(record.cnpj());
            boolean persistStation = false;
            if (station == null) {
                station = createStation(record);
                created++;
                persistStation = true;
            } else {
                List<String> changedFields = differingStationFields(station, record);
                if (!changedFields.isEmpty()) {
                    updated++;
                    persistStation = true;
                    errors.add("CNPJ " + record.cnpj()
                            + " teve dados cadastrais atualizados pelo CSV nos campos: "
                            + String.join(", ", changedFields) + ".");
                }
            }
            if (persistStation) {
                applyStationDetails(station, record);
                station = stationRepository.save(station);
            }
            if (station.getLatitude() == null || station.getLongitude() == null) {
                withoutCoordinates++;
            }
            stations.put(record.cnpj(), station);
        }
        return new StationPreparation(stations, created, updated, withoutCoordinates);
    }

    private boolean sameStationDetails(StationRecord first, StationRecord second) {
        return java.util.Objects.equals(first.corporateName(), second.corporateName())
                && java.util.Objects.equals(first.brand(), second.brand())
                && java.util.Objects.equals(first.street(), second.street())
                && java.util.Objects.equals(first.number(), second.number())
                && java.util.Objects.equals(first.neighborhood(), second.neighborhood())
                && java.util.Objects.equals(first.postalCode(), second.postalCode())
                && java.util.Objects.equals(first.city(), second.city())
                && java.util.Objects.equals(first.state(), second.state());
    }

    private List<String> differingStationFields(Station station, StationRecord record) {
        List<String> fields = new ArrayList<>();
        addChangedField(fields, "revenda", station.getCorporateName(), record.corporateName());
        addChangedField(fields, "bandeira", station.getBrand(), record.brand());
        addChangedField(fields, "rua", station.getStreet(), record.street());
        addChangedField(fields, "número", station.getNumber(), record.number());
        addChangedField(fields, "bairro", station.getNeighborhood(), record.neighborhood());
        addChangedField(fields, "CEP", station.getPostalCode(), record.postalCode());
        addChangedField(fields, "município", station.getCity(), record.city());
        addChangedField(fields, "UF", station.getState(), record.state());
        return fields;
    }

    private void addChangedField(
            List<String> fields,
            String label,
            String currentValue,
            String nextValue) {
        if (!java.util.Objects.equals(trimToNull(currentValue), trimToNull(nextValue))) {
            fields.add(label);
        }
    }

    private StationRecord parseStationRecord(AnpCsvRow row) {
        Map<String, String> values = row.values();
        String cnpj = normalizeCnpj(values.get("cnpj da revenda"));
        String state = requiredText(values.get("estado sigla"), "estado").toUpperCase(Locale.ROOT);
        if (!"SP".equals(state)) {
            throw new AnpRecordException("a importação aceita somente postos do estado SP.");
        }
        return new StationRecord(
                cnpj,
                requiredText(values.get("revenda"), "revenda"),
                trimToNull(values.get("bandeira")),
                trimToNull(values.get("nome da rua")),
                trimToNull(values.get("numero rua")),
                trimToNull(values.get("bairro")),
                trimToNull(values.get("cep")),
                requiredText(values.get("municipio"), "município"),
                state);
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

    private Station createStation(StationRecord record) {
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

    private void applyStationDetails(Station station, StationRecord record) {
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

    private int addressCompleteness(StationRecord record) {
        int score = 0;
        if (record.street() != null) score += 4;
        if (record.number() != null) score += 2;
        if (record.neighborhood() != null) score += 2;
        if (record.postalCode() != null) score++;
        return score;
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

    private record StationRecord(
            String cnpj,
            String corporateName,
            String brand,
            String street,
            String number,
            String neighborhood,
            String postalCode,
            String city,
            String state) {
    }

    private int countInvalidRows(Set<String> errors) {
        return (int) errors.stream()
                .filter(error -> error.startsWith("Linha "))
                .map(error -> error.substring("Linha ".length(), error.indexOf(':')))
                .distinct()
                .count();
    }

    private record StateFilteredRows(List<AnpCsvRow> rows, int otherStateRows) {
    }

    private record StationPreparation(
            Map<String, Station> stations,
            int created,
            int updated,
            int withoutCoordinates) {
    }

    private record PriceKey(String cnpj, String fuelCode, LocalDate collectionDate) {
    }
}

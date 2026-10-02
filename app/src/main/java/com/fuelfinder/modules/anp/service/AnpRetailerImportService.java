package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

@Service
public class AnpRetailerImportService {

    private static final Pattern VALID_CNPJ = Pattern.compile("[0-9]{14}");
    private static final long PAGE_DELAY_MILLIS = 200;
    private final AnpRetailerApiClient apiClient;
    private final AnpCoordinateUpdater coordinateUpdater;

    public AnpRetailerImportService(
            AnpRetailerApiClient apiClient,
            AnpCoordinateUpdater coordinateUpdater) {
        this.apiClient = apiClient;
        this.coordinateUpdater = coordinateUpdater;
    }

    public AnpCoordinateUpdateResult updateCoordinates(
            Set<String> importedCnpjs,
            IntConsumer pageProgress) {
        if (importedCnpjs.isEmpty()) {
            return new AnpCoordinateUpdateResult(0, 0, 0, 0, 0, List.of());
        }
        Map<String, AnpRetailerRecord> apiStations = new HashMap<>();
        Set<String> conflictingCnpjs = new HashSet<>();
        Set<String> apiCnpjsWithoutCoordinates = new HashSet<>();
        Set<String> pageFingerprints = new HashSet<>();
        List<String> errors = new ArrayList<>();
        int pageNumber = 1;
        int pagesProcessed = 0;

        while (true) {
            if (pageNumber > 1) {
                pauseBetweenPages();
            }
            AnpRetailerPage page = apiClient.getSaoPauloPage(pageNumber);
            pagesProcessed++;
            pageProgress.accept(pagesProcessed);
            if (page.records().isEmpty()) {
                break;
            }
            String pageFingerprint = page.records().stream()
                    .map(record -> java.util.Objects.toString(
                            normalizeCnpj(record.cnpj()),
                            "INVALID"))
                    .sorted()
                    .toList()
                    .toString();
            if (!pageFingerprints.add(pageFingerprint)) {
                throw new ExternalServiceException(
                        "A API da ANP repetiu os registros da página "
                                + pageNumber + "; a paginação foi interrompida.", null);
            }

            for (AnpRetailerRecord record : page.records()) {
                if (record.state() == null || !"SP".equalsIgnoreCase(record.state().trim())) {
                    errors.add("Página " + pageNumber
                            + ": registro da API ignorado por não confirmar UF SP.");
                    continue;
                }
                String cnpj = normalizeCnpj(record.cnpj());
                if (cnpj == null) {
                    errors.add("Página " + pageNumber
                            + ": registro da API ignorado por CNPJ ausente ou inválido.");
                    continue;
                }
                if (!importedCnpjs.contains(cnpj)) {
                    continue;
                }

                AnpRetailerRecord candidate = new AnpRetailerRecord(
                        cnpj,
                        "SP",
                        trimToNull(record.latitude()),
                        trimToNull(record.longitude()));
                if (!hasCoordinates(candidate)) {
                    apiCnpjsWithoutCoordinates.add(cnpj);
                    errors.add("Página " + pageNumber
                            + ": a API não retornou coordenadas válidas para o CNPJ "
                            + cnpj + ".");
                }
                AnpRetailerRecord previous = apiStations.putIfAbsent(cnpj, candidate);
                if (previous != null && hasDifferentCoordinates(previous, candidate)) {
                    conflictingCnpjs.add(cnpj);
                    errors.add("Página " + pageNumber
                            + ": CNPJ " + cnpj
                            + " aparece com coordenadas conflitantes na API da ANP.");
                } else if (previous != null
                        && !hasCoordinates(previous)
                        && hasCoordinates(candidate)) {
                    apiStations.put(cnpj, candidate);
                }
            }

            if (pageNumber == Integer.MAX_VALUE) {
                throw new ExternalServiceException(
                        "A API da ANP excedeu o número máximo de páginas suportado.", null);
            }
            pageNumber++;
        }

        AnpCoordinateUpdateResult update = coordinateUpdater.update(
                importedCnpjs,
                apiStations,
                conflictingCnpjs,
                errors);
        return new AnpCoordinateUpdateResult(
                pagesProcessed,
                update.coordinatesUpdated(),
                update.apiCnpjsUnmatched(),
                Math.max(
                        update.apiStationsWithoutCoordinates(),
                        apiCnpjsWithoutCoordinates.size()),
                update.stationsWithoutCoordinates(),
                update.errors());
    }

    private String normalizeCnpj(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9]", "");
        return VALID_CNPJ.matcher(normalized).matches() ? normalized : null;
    }

    private boolean hasCoordinates(AnpRetailerRecord record) {
        return isValidCoordinate(record.latitude(), -90, 90)
                && isValidCoordinate(record.longitude(), -180, 180);
    }

    private boolean hasDifferentCoordinates(
            AnpRetailerRecord first,
            AnpRetailerRecord second) {
        if (!hasCoordinates(first) || !hasCoordinates(second)) {
            return false;
        }
        return new BigDecimal(first.latitude()).compareTo(new BigDecimal(second.latitude())) != 0
                || new BigDecimal(first.longitude()).compareTo(new BigDecimal(second.longitude())) != 0;
    }

    private boolean isValidCoordinate(String value, double minimum, double maximum) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            double coordinate = new BigDecimal(value.trim()).doubleValue();
            return Double.isFinite(coordinate)
                    && coordinate >= minimum
                    && coordinate <= maximum;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void pauseBetweenPages() {
        try {
            Thread.sleep(PAGE_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(
                    "A consulta paginada da API da ANP foi interrompida.", exception);
        }
    }
}

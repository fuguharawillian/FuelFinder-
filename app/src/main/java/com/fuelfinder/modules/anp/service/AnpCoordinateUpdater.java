package com.fuelfinder.modules.anp.service;

import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AnpCoordinateUpdater {

    private final StationRepository stationRepository;

    public AnpCoordinateUpdater(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Transactional
    public AnpCoordinateUpdateResult update(
            Set<String> importedCnpjs,
            Map<String, AnpRetailerRecord> apiStations,
            Set<String> conflictingApiCnpjs,
            List<String> apiErrors) {
        List<String> errors = new ArrayList<>(apiErrors);
        Map<String, Station> stations = stationRepository.findByCnpjIn(importedCnpjs).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Station::getCnpj,
                        station -> station));
        int coordinatesUpdated = 0;
        int apiCnpjsUnmatched = 0;
        int apiStationsWithoutCoordinates = 0;
        int stationsWithoutCoordinates = 0;

        for (String cnpj : importedCnpjs) {
            Station station = stations.get(cnpj);
            if (station == null) {
                errors.add("CNPJ " + cnpj
                        + " não corresponde a um posto salvo no CSV; coordenadas não atualizadas.");
                apiCnpjsUnmatched++;
                continue;
            }

            AnpRetailerRecord apiStation = apiStations.get(cnpj);
            if (apiStation == null || conflictingApiCnpjs.contains(cnpj)) {
                errors.add("CNPJ " + cnpj
                        + (apiStation == null
                                ? " não encontrado na API da ANP."
                                : " possui registros conflitantes na API da ANP."));
                apiCnpjsUnmatched++;
                if (!hasCoordinates(station)) {
                    stationsWithoutCoordinates++;
                }
                continue;
            }

            BigDecimal latitude = parseCoordinate(apiStation.latitude(), -90, 90);
            BigDecimal longitude = parseCoordinate(apiStation.longitude(), -180, 180);
            if (latitude == null || longitude == null) {
                errors.add("A API da ANP não retornou latitude e longitude válidas para o CNPJ "
                        + cnpj + "; coordenadas existentes foram preservadas.");
                apiStationsWithoutCoordinates++;
            } else if (station.getLatitude() == null
                    || station.getLongitude() == null
                    || latitude.compareTo(station.getLatitude()) != 0
                    || longitude.compareTo(station.getLongitude()) != 0) {
                station.setLatitude(latitude);
                station.setLongitude(longitude);
                stationRepository.save(station);
                coordinatesUpdated++;
            }
            if (!hasCoordinates(station)) {
                stationsWithoutCoordinates++;
            }
        }

        return new AnpCoordinateUpdateResult(
                0,
                coordinatesUpdated,
                apiCnpjsUnmatched,
                apiStationsWithoutCoordinates,
                stationsWithoutCoordinates,
                List.copyOf(errors));
    }

    private BigDecimal parseCoordinate(String value, double minimum, double maximum) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            BigDecimal coordinate = new BigDecimal(value.trim());
            double numericValue = coordinate.doubleValue();
            if (!Double.isFinite(numericValue)
                    || numericValue < minimum
                    || numericValue > maximum) {
                return null;
            }
            return coordinate.setScale(7, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasCoordinates(Station station) {
        return station.getLatitude() != null && station.getLongitude() != null;
    }
}

package com.fuelfinder.modules.station.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.DuplicateResourceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.common.util.HaversineCalculator;
import com.fuelfinder.modules.station.dto.CreateStationRequestDTO;
import com.fuelfinder.modules.station.dto.StationResponseDTO;
import com.fuelfinder.modules.station.dto.UpdateStationRequestDTO;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.entity.StationStatus;
import com.fuelfinder.modules.station.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class StationService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    private final StationRepository stationRepository;
    private final GeoapifyGeocodingService geocodingService;

    public StationService(
            StationRepository stationRepository,
            GeoapifyGeocodingService geocodingService) {
        this.stationRepository = stationRepository;
        this.geocodingService = geocodingService;
    }

    public List<StationResponseDTO> search(
            Double latitude,
            Double longitude,
            Double radiusKm,
            String query) {
        boolean hasCoordinates = latitude != null || longitude != null;
        if (hasCoordinates && (latitude == null || longitude == null)) {
            throw new BusinessException("Latitude e longitude devem ser informadas juntas.");
        }
        if (hasCoordinates) {
            validateCoordinates(latitude, longitude);
        }
        double radius = radiusKm == null ? 5.0 : radiusKm;
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new BusinessException("O raio de busca deve ser maior que zero.");
        }
        if (query != null && !query.isBlank()) {
            String trimmedQuery = query.trim();
            String postalCode = normalizePostalCode(trimmedQuery);
            if (postalCode != null) {
                if (!hasCoordinates) {
                    return stationRepository.searchByPostalCode(StationStatus.ACTIVE, postalCode)
                            .stream()
                            .map(station -> toResponse(station, null))
                            .toList();
                }
            } else {
                var point = geocodingService.geocode(trimmedQuery);
                if (point.isPresent()) {
                    validateCoordinates(point.get().latitude(), point.get().longitude());
                    return findNearby(point.get().latitude(), point.get().longitude(), radius);
                }
            }
            if (!hasCoordinates) {
                return stationRepository.searchByText(StationStatus.ACTIVE, trimmedQuery)
                        .stream()
                        .map(station -> toResponse(station, null))
                        .toList();
            }
        }
        if (!hasCoordinates) {
            throw new BusinessException(
                    "Informe latitude e longitude ou um termo de busca.");
        }
        return findNearby(latitude, longitude, radius);
    }

    private String normalizePostalCode(String query) {
        if (!query.matches("[\\d\\s.-]+")) {
            return null;
        }
        String digits = query.replaceAll("\\D", "");
        return digits.length() == 8 ? digits : null;
    }

    public StationResponseDTO findActiveById(UUID stationId) {
        Station station = stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));
        return toResponse(station, null);
    }

    @Transactional
    public StationResponseDTO create(CreateStationRequestDTO request) {
        String cnpj = normalizeCnpj(request.cnpj());
        if (stationRepository.existsByCnpj(cnpj)) {
            throw new DuplicateResourceException(
                    "Já existe um posto cadastrado com o CNPJ: " + cnpj);
        }
        Station station = new Station(
                cnpj,
                request.corporateName().trim(),
                trimToNull(request.tradeName()),
                trimToNull(request.brand()),
                trimToNull(request.street()),
                trimToNull(request.number()),
                trimToNull(request.neighborhood()),
                request.city().trim(),
                request.state().trim().toUpperCase(),
                trimToNull(request.postalCode()),
                request.latitude(),
                request.longitude());
        return toResponse(stationRepository.save(station), null);
    }

    @Transactional
    public StationResponseDTO update(UUID stationId, UpdateStationRequestDTO request) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));
        if (request.cnpj() != null && !request.cnpj().isBlank()) {
            String cnpj = normalizeCnpj(request.cnpj());
            if (!cnpj.equals(station.getCnpj())) {
                if (stationRepository.existsByCnpj(cnpj)) {
                    throw new DuplicateResourceException(
                            "Já existe um posto cadastrado com o CNPJ: " + cnpj);
                }
            }
            station.setCnpj(cnpj);
        }
        if (request.corporateName() != null) {
            station.setCorporateName(requireText(request.corporateName(), "A razão social"));
        }
        if (request.tradeName() != null) {
            station.setTradeName(trimToNull(request.tradeName()));
        }
        if (request.brand() != null) {
            station.setBrand(trimToNull(request.brand()));
        }
        if (request.street() != null) {
            station.setStreet(trimToNull(request.street()));
        }
        if (request.number() != null) {
            station.setNumber(trimToNull(request.number()));
        }
        if (request.neighborhood() != null) {
            station.setNeighborhood(trimToNull(request.neighborhood()));
        }
        if (request.city() != null) {
            station.setCity(requireText(request.city(), "A cidade"));
        }
        if (request.state() != null) {
            station.setState(request.state().trim().toUpperCase());
        }
        if (request.postalCode() != null) {
            station.setPostalCode(trimToNull(request.postalCode()));
        }
        if (request.latitude() != null) {
            station.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            station.setLongitude(request.longitude());
        }
        return toResponse(stationRepository.save(station), null);
    }

    @Transactional
    public void deactivate(UUID stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));
        station.setStatus(StationStatus.INACTIVE);
        stationRepository.save(station);
    }

    private List<StationResponseDTO> findNearby(double latitude, double longitude, double radiusKm) {
        double angularRadius = radiusKm / EARTH_RADIUS_KM;
        double minimumLatitude = Math.max(-90.0,
                latitude - Math.toDegrees(angularRadius));
        double maximumLatitude = Math.min(90.0,
                latitude + Math.toDegrees(angularRadius));
        List<Station> candidates;
        if (angularRadius >= Math.PI
                || minimumLatitude <= -90.0 || maximumLatitude >= 90.0) {
            candidates = stationRepository.findByStatusAndLatitudeBetween(
                    StationStatus.ACTIVE,
                    BigDecimal.valueOf(minimumLatitude),
                    BigDecimal.valueOf(maximumLatitude));
        } else {
            double longitudeDelta = Math.toDegrees(Math.asin(
                    Math.sin(angularRadius) / Math.cos(Math.toRadians(latitude))));
            double minimumLongitude = longitude - longitudeDelta;
            double maximumLongitude = longitude + longitudeDelta;
            if (minimumLongitude < -180.0) {
                candidates = stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                        StationStatus.ACTIVE,
                        BigDecimal.valueOf(minimumLatitude),
                        BigDecimal.valueOf(maximumLatitude),
                        MIN_LONGITUDE,
                        BigDecimal.valueOf(maximumLongitude))
                        .stream()
                        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                candidates.addAll(stationRepository
                        .findByStatusAndLatitudeBetweenAndLongitudeBetween(
                                StationStatus.ACTIVE,
                                BigDecimal.valueOf(minimumLatitude),
                                BigDecimal.valueOf(maximumLatitude),
                                BigDecimal.valueOf(minimumLongitude + 360.0),
                                MAX_LONGITUDE));
            } else if (maximumLongitude > 180.0) {
                candidates = new java.util.ArrayList<>(
                        stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                                StationStatus.ACTIVE,
                                BigDecimal.valueOf(minimumLatitude),
                                BigDecimal.valueOf(maximumLatitude),
                                BigDecimal.valueOf(minimumLongitude),
                                MAX_LONGITUDE));
                candidates.addAll(stationRepository
                        .findByStatusAndLatitudeBetweenAndLongitudeBetween(
                                StationStatus.ACTIVE,
                                BigDecimal.valueOf(minimumLatitude),
                                BigDecimal.valueOf(maximumLatitude),
                                MIN_LONGITUDE,
                                BigDecimal.valueOf(maximumLongitude - 360.0)));
            } else {
                candidates = stationRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                        StationStatus.ACTIVE,
                        BigDecimal.valueOf(minimumLatitude),
                        BigDecimal.valueOf(maximumLatitude),
                        BigDecimal.valueOf(minimumLongitude),
                        BigDecimal.valueOf(maximumLongitude));
            }
        }
        return candidates.stream()
                .map(station -> {
                    double distance = HaversineCalculator.calculateDistanceKm(
                            latitude,
                            longitude,
                            station.getLatitude().doubleValue(),
                            station.getLongitude().doubleValue());
                    return new StationDistance(station, distance);
                })
                .filter(station -> station.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(StationDistance::distanceKm))
                .map(station -> toResponse(station.station(), station.distanceKm()))
                .toList();
    }

    private void validateCoordinates(double latitude, double longitude) {
        try {
            HaversineCalculator.calculateDistanceKm(latitude, longitude, latitude, longitude);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
    }

    private String normalizeCnpj(String cnpj) {
        String normalized = cnpj.replaceAll("[^0-9]", "");
        if (normalized.length() != 14) {
            throw new BusinessException("O CNPJ deve conter 14 dígitos.");
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value.isBlank()) {
            throw new BusinessException(fieldName + " não pode ser vazio.");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private StationResponseDTO toResponse(Station station, Double distanceKm) {
        String address = java.util.stream.Stream.of(
                        station.getStreet(),
                        station.getNumber(),
                        station.getNeighborhood())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        return new StationResponseDTO(
                station.getId(),
                station.getCnpj(),
                station.getCorporateName(),
                station.getTradeName(),
                station.getBrand(),
                address,
                station.getCity(),
                station.getState(),
                station.getPostalCode(),
                station.getLatitude(),
                station.getLongitude(),
                distanceKm,
                station.getAverageRating(),
                station.getTotalReviews(),
                station.getStatus().name());
    }

    private record StationDistance(Station station, double distanceKm) {
    }
}

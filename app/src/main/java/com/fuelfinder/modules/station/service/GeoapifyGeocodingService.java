package com.fuelfinder.modules.station.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Service
public class GeoapifyGeocodingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeoapifyGeocodingService.class);
    private static final String GEOCODING_URL = "/v1/geocode/search";

    private final RestClient restClient;
    private final String apiKey;

    public GeoapifyGeocodingService(
            RestClient.Builder restClientBuilder,
            @Value("${geoapify.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Optional<GeoPoint> geocode(String query) {
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }
        try {
            GeoapifyResponse response = restClient.get()
                    .uri(builder -> builder
                            .scheme("https")
                            .host("api.geoapify.com")
                            .path(GEOCODING_URL)
                            .queryParam("text", query)
                            .queryParam("format", "json")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(GeoapifyResponse.class);
            if (response == null || response.results() == null) {
                return Optional.empty();
            }
            return response.results().stream()
                    .filter(result -> result.lat() != null && result.lon() != null)
                    .findFirst()
                    .map(result -> new GeoPoint(result.lat(), result.lon()));
        } catch (RestClientException exception) {
            LOGGER.warn("Geoapify geocoding request failed.");
            throw new ExternalServiceException(
                    "Não foi possível concluir a busca geográfica neste momento.", exception);
        }
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public record GeoPoint(double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoapifyResponse(List<GeoapifyResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoapifyResult(Double lat, Double lon) {
    }
}

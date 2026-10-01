package com.fuelfinder.modules.price.controller;

import com.fuelfinder.modules.auth.AuthPrincipal;
import com.fuelfinder.modules.price.dto.CompareResultDTO;
import com.fuelfinder.modules.price.dto.CreateFuelPriceRequestDTO;
import com.fuelfinder.modules.price.dto.FuelPriceResponseDTO;
import com.fuelfinder.modules.price.dto.UpdateFuelPriceRequestDTO;
import com.fuelfinder.modules.price.service.FuelPriceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class FuelPriceController {

    private final FuelPriceService fuelPriceService;

    public FuelPriceController(FuelPriceService fuelPriceService) {
        this.fuelPriceService = fuelPriceService;
    }

    @GetMapping("/stations/{stationId}/fuel-prices")
    public ResponseEntity<List<FuelPriceResponseDTO>> getLatestPrices(
            @PathVariable UUID stationId) {
        return ResponseEntity.ok(fuelPriceService.getLatestPrices(stationId));
    }

    @PostMapping("/stations/{stationId}/fuel-prices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FuelPriceResponseDTO> create(
            @PathVariable UUID stationId,
            @Valid @RequestBody CreateFuelPriceRequestDTO request) {
        FuelPriceResponseDTO created = fuelPriceService.create(stationId, request);
        return ResponseEntity.created(URI.create(
                "/stations/" + stationId + "/fuel-prices/" + created.id()))
                .body(created);
    }

    @PatchMapping("/stations/{stationId}/fuel-prices/{priceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FuelPriceResponseDTO> update(
            @PathVariable UUID stationId,
            @PathVariable UUID priceId,
            @Valid @RequestBody UpdateFuelPriceRequestDTO request) {
        return ResponseEntity.ok(fuelPriceService.update(stationId, priceId, request));
    }

    @GetMapping("/fuel-prices/compare")
    public ResponseEntity<List<CompareResultDTO>> compare(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(required = false) String fuelTypeCode,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(defaultValue = "PRICE") String sortBy,
            @AuthenticationPrincipal AuthPrincipal principal) {
        UUID userId = principal == null ? null : principal.userId();
        return ResponseEntity.ok(fuelPriceService.compare(
                latitude, longitude, radiusKm, fuelTypeCode, vehicleId, userId, sortBy));
    }
}

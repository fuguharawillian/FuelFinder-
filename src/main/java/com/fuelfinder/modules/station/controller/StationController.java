package com.fuelfinder.modules.station.controller;

import com.fuelfinder.modules.station.dto.CreateStationRequestDTO;
import com.fuelfinder.modules.station.dto.StationResponseDTO;
import com.fuelfinder.modules.station.dto.UpdateStationRequestDTO;
import com.fuelfinder.modules.station.service.StationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public ResponseEntity<List<StationResponseDTO>> search(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(stationService.search(latitude, longitude, radiusKm, query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StationResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(stationService.findActiveById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StationResponseDTO> create(
            @Valid @RequestBody CreateStationRequestDTO request) {
        StationResponseDTO created = stationService.create(request);
        return ResponseEntity.created(URI.create("/stations/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StationResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStationRequestDTO request) {
        return ResponseEntity.ok(stationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        stationService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}

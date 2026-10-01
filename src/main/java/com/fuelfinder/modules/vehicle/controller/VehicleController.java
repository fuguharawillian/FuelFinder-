package com.fuelfinder.modules.vehicle.controller;

import com.fuelfinder.modules.auth.AuthPrincipal;
import com.fuelfinder.modules.vehicle.dto.CreateVehicleRequestDTO;
import com.fuelfinder.modules.vehicle.dto.UpdateVehicleRequestDTO;
import com.fuelfinder.modules.vehicle.dto.VehicleResponseDTO;
import com.fuelfinder.modules.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vehicles")
@PreAuthorize("hasRole('DRIVER')")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponseDTO> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateVehicleRequestDTO request) {
        VehicleResponseDTO created = vehicleService.create(request, principal.userId());
        return ResponseEntity.created(URI.create("/vehicles/" + created.id()))
                .body(created);
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponseDTO>> listMine(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(vehicleService.listByUser(principal.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponseDTO> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(vehicleService.findByIdAndUser(id, principal.userId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VehicleResponseDTO> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateVehicleRequestDTO request) {
        return ResponseEntity.ok(vehicleService.update(id, request, principal.userId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        vehicleService.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}

package com.fuelfinder.modules.recommendation.controller;

import com.fuelfinder.modules.auth.AuthPrincipal;
import com.fuelfinder.modules.recommendation.dto.RecommendationResponseDTO;
import com.fuelfinder.modules.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/recommendations")
@PreAuthorize("hasRole('DRIVER')")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/fuel")
    public ResponseEntity<RecommendationResponseDTO> recommendFuel(
            @RequestParam UUID vehicleId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5") double radiusKm,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(recommendationService.recommend(
                vehicleId,
                principal.userId(),
                latitude,
                longitude,
                radiusKm));
    }
}

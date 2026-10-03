package com.fuelfinder.modules.review.controller;

import com.fuelfinder.modules.auth.AuthPrincipal;
import com.fuelfinder.modules.review.dto.CreateReviewRequestDTO;
import com.fuelfinder.modules.review.dto.ModerateReviewRequestDTO;
import com.fuelfinder.modules.review.dto.ReviewResponseDTO;
import com.fuelfinder.modules.review.entity.ReviewStatus;
import com.fuelfinder.modules.review.service.ReviewService;
import com.fuelfinder.modules.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReviewResponseDTO>> listForModeration(
            @RequestParam(defaultValue = "PENDING") ReviewStatus status) {
        return ResponseEntity.ok(reviewService.listByStatus(status));
    }

    @GetMapping("/stations/{stationId}/reviews")
    public ResponseEntity<List<ReviewResponseDTO>> listByStation(
            @PathVariable UUID stationId) {
        return ResponseEntity.ok(reviewService.listApproved(stationId));
    }

    @PostMapping("/stations/{stationId}/reviews")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ReviewResponseDTO> create(
            @PathVariable UUID stationId,
            @Valid @RequestBody CreateReviewRequestDTO request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        ReviewResponseDTO response = reviewService.createOrUpdate(
                stationId, request, principal.userId());
        return ResponseEntity.created(URI.create(
                "/stations/" + stationId + "/reviews/" + response.id()))
                .body(response);
    }

    @PatchMapping("/reviews/{id}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ReviewResponseDTO> updateOwn(
            @PathVariable UUID id,
            @Valid @RequestBody CreateReviewRequestDTO request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                reviewService.updateOwn(id, request, principal.userId()));
    }

    @PatchMapping("/reviews/{id}/moderation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponseDTO> moderate(
            @PathVariable UUID id,
            @Valid @RequestBody ModerateReviewRequestDTO request) {
        return ResponseEntity.ok(reviewService.moderate(id, request.status()));
    }

    @DeleteMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthPrincipal principal,
            Authentication authentication) {
        Role role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.equals(Role.ROLE_DRIVER.name())
                        || authority.equals(Role.ROLE_ADMIN.name()))
                .map(Role::valueOf)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "O usuário autenticado não possui um papel reconhecido."));
        reviewService.delete(id, principal.userId(), role);
        return ResponseEntity.noContent().build();
    }
}

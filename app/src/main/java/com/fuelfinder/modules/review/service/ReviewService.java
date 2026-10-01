package com.fuelfinder.modules.review.service;

import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.service.AccountForbiddenException;
import com.fuelfinder.modules.review.dto.CreateReviewRequestDTO;
import com.fuelfinder.modules.review.dto.ReviewResponseDTO;
import com.fuelfinder.modules.review.entity.Review;
import com.fuelfinder.modules.review.entity.ReviewStatus;
import com.fuelfinder.modules.review.repository.ReviewRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.entity.StationStatus;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final StationRepository stationRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            StationRepository stationRepository,
            UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.stationRepository = stationRepository;
        this.userRepository = userRepository;
    }

    public List<ReviewResponseDTO> listApproved(UUID stationId) {
        requireActiveStation(stationId);
        List<Review> reviews = reviewRepository
                .findByStationIdAndStatusOrderByCreatedAtDesc(
                        stationId, ReviewStatus.APPROVED);
        Map<UUID, User> users = StreamSupport.stream(userRepository.findAllById(
                        reviews.stream().map(Review::getUserId).distinct().toList()).spliterator(),
                        false)
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return reviews.stream()
                .map(review -> toResponse(review, users.get(review.getUserId())))
                .toList();
    }

    public List<ReviewResponseDTO> listByStatus(ReviewStatus status) {
        List<Review> reviews = reviewRepository.findAllByStatusOrderByCreatedAtDesc(status);
        Map<UUID, User> users = StreamSupport.stream(userRepository.findAllById(
                        reviews.stream().map(Review::getUserId).distinct().toList()).spliterator(),
                        false)
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return reviews.stream()
                .map(review -> toResponse(review, users.get(review.getUserId())))
                .toList();
    }

    @Transactional
    public ReviewResponseDTO createOrUpdate(
            UUID stationId,
            CreateReviewRequestDTO request,
            UUID userId) {
        Station station = requireActiveStation(stationId);
        User user = requireUser(userId);
        Review review = reviewRepository.findByUserIdAndStationId(userId, stationId)
                .orElseGet(() -> new Review(userId, station, request.rating(),
                        normalizeComment(request.comment())));

        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        Review saved = reviewRepository.save(review);
        updateStationRating(stationId);
        return toResponse(saved, user);
    }

    @Transactional
    public ReviewResponseDTO updateOwn(
            UUID reviewId,
            CreateReviewRequestDTO request,
            UUID userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação não encontrada."));
        requireOwner(review, userId);
        User user = requireUser(userId);
        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        Review saved = reviewRepository.save(review);
        updateStationRating(saved.getStation().getId());
        return toResponse(saved, user);
    }

    @Transactional
    public ReviewResponseDTO moderate(UUID reviewId, ReviewStatus status) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação não encontrada."));
        User user = requireUser(review.getUserId());
        review.setStatus(status);
        Review saved = reviewRepository.save(review);
        updateStationRating(saved.getStation().getId());
        return toResponse(saved, user);
    }

    @Transactional
    public void delete(UUID reviewId, UUID userId, Role role) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação não encontrada."));
        if (role != Role.ROLE_ADMIN) {
            requireOwner(review, userId);
        }
        UUID stationId = review.getStation().getId();
        reviewRepository.delete(review);
        updateStationRating(stationId);
    }

    private Station requireActiveStation(UUID stationId) {
        return stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuário da avaliação não encontrado."));
    }

    private void requireOwner(Review review, UUID userId) {
        if (!review.getUserId().equals(userId)) {
            throw new AccountForbiddenException(
                    "Você só pode alterar ou excluir sua própria avaliação.");
        }
    }

    private void updateStationRating(UUID stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));
        BigDecimal averageRating = reviewRepository.calculateAverageRating(
                        stationId, ReviewStatus.APPROVED)
                .map(average -> BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        long totalReviews = reviewRepository.countByStationIdAndStatus(
                stationId, ReviewStatus.APPROVED);
        station.setAverageRating(averageRating);
        station.setTotalReviews(Math.toIntExact(totalReviews));
        stationRepository.save(station);
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private ReviewResponseDTO toResponse(Review review, User user) {
        return new ReviewResponseDTO(
                review.getId(),
                review.getUserId(),
                user.getFullName(),
                review.getStation().getId(),
                review.getRating(),
                review.getComment(),
                review.getStatus().name(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}

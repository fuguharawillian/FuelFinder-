package com.fuelfinder.modules.review.repository;

import com.fuelfinder.modules.review.entity.Review;
import com.fuelfinder.modules.review.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByStationIdAndStatusOrderByCreatedAtDesc(
            UUID stationId,
            ReviewStatus status);

    List<Review> findAllByStatusOrderByCreatedAtDesc(ReviewStatus status);

    Optional<Review> findByUserIdAndStationId(UUID userId, UUID stationId);

    Optional<Review> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT AVG(review.rating) FROM Review review
            WHERE review.station.id = :stationId AND review.status = :status
            """)
    Optional<Double> calculateAverageRating(
            @Param("stationId") UUID stationId,
            @Param("status") ReviewStatus status);

    long countByStationIdAndStatus(UUID stationId, ReviewStatus status);
}

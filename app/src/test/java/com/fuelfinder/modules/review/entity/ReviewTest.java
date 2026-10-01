package com.fuelfinder.modules.review.entity;

import com.fuelfinder.modules.station.entity.Station;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReviewTest {

    @Test
    void exposesReviewFieldsAndInitializesTimestamps() {
        UUID userId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        Station station = new Station(
                "12345678901234", "Corporate", "Trade", "Brand",
                "Street", "1", "Center", "City", "SP", "00000-000",
                BigDecimal.ZERO, BigDecimal.ZERO);
        Review review = new Review(userId, station, 5, "Good");
        review.setId(reviewId);
        review.setUserId(UUID.randomUUID());
        review.setStation(null);
        review.setUserId(userId);
        review.setStation(station);
        review.setRating(4);
        review.setComment("Updated");
        review.setStatus(ReviewStatus.PENDING);
        review.initializeTimestamps();
        LocalDateTime createdAt = review.getCreatedAt();
        review.initializeTimestamps();
        review.updateTimestamp();

        assertEquals(reviewId, review.getId());
        assertEquals(userId, review.getUserId());
        assertEquals(station, review.getStation());
        assertEquals(4, review.getRating());
        assertEquals("Updated", review.getComment());
        assertEquals(ReviewStatus.PENDING, review.getStatus());
        assertNotNull(createdAt);
        assertEquals(createdAt, review.getCreatedAt());
        assertNotNull(review.getUpdatedAt());
    }

    @Test
    void defaultsNewReviewsToApproved() {
        Review review = new Review(UUID.randomUUID(), null, 5, null);

        assertEquals(ReviewStatus.APPROVED, review.getStatus());
    }
}

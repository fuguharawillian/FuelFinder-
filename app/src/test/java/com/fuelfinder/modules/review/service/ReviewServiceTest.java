package com.fuelfinder.modules.review.service;

import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.service.AccountForbiddenException;
import com.fuelfinder.modules.review.dto.CreateReviewRequestDTO;
import com.fuelfinder.modules.review.entity.Review;
import com.fuelfinder.modules.review.entity.ReviewStatus;
import com.fuelfinder.modules.review.repository.ReviewRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.entity.StationStatus;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewService reviewService;

    private final UUID stationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private Station station;
    private User user;
    private Review review;

    @BeforeEach
    void setUp() {
        station = station(stationId);
        user = user(userId, "Driver");
        review = new Review(userId, station, 4, "Good");
        review.setId(reviewId);
    }

    @Test
    void listsOnlyApprovedReviewsForActiveStationsAndMapsUserNames() {
        when(stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE))
                .thenReturn(Optional.of(station));
        when(reviewRepository.findByStationIdAndStatusOrderByCreatedAtDesc(
                stationId, ReviewStatus.APPROVED)).thenReturn(List.of(review));
        when(userRepository.findAllById(List.of(userId))).thenReturn(List.of(user));

        var result = reviewService.listApproved(stationId);

        assertEquals(1, result.size());
        assertEquals("Driver", result.get(0).userName());
        assertEquals(ReviewStatus.APPROVED.name(), result.get(0).status());
    }

    @Test
    void listsReviewsByStatusForAdministrativeModeration() {
        review.setStatus(ReviewStatus.PENDING);
        when(reviewRepository.findAllByStatusOrderByCreatedAtDesc(ReviewStatus.PENDING))
                .thenReturn(List.of(review));
        when(userRepository.findAllById(List.of(userId))).thenReturn(List.of(user));

        var result = reviewService.listByStatus(ReviewStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals("Driver", result.get(0).userName());
        assertEquals(ReviewStatus.PENDING.name(), result.get(0).status());
    }

    @Test
    void rejectsListingForMissingOrInactiveStations() {
        when(stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.listApproved(stationId));
    }

    @Test
    void createsApprovedReviewAndRecalculatesAggregate() {
        when(stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE))
                .thenReturn(Optional.of(station));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reviewRepository.findByUserIdAndStationId(userId, stationId))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(reviewRepository.calculateAverageRating(stationId, ReviewStatus.APPROVED))
                .thenReturn(Optional.of(4.5));
        when(reviewRepository.countByStationIdAndStatus(stationId, ReviewStatus.APPROVED))
                .thenReturn(2L);

        var response = reviewService.createOrUpdate(
                stationId, new CreateReviewRequestDTO(5, "  Helpful  "), userId);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertEquals(ReviewStatus.APPROVED, saved.getValue().getStatus());
        assertEquals("Helpful", saved.getValue().getComment());
        assertEquals("Driver", response.userName());
        assertEquals(new BigDecimal("4.50"), station.getAverageRating());
        assertEquals(2, station.getTotalReviews());
        verify(stationRepository).save(station);
    }

    @Test
    void updatesExistingReviewWithoutBypassingItsModerationStatus() {
        review.setStatus(ReviewStatus.REJECTED);
        when(stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE))
                .thenReturn(Optional.of(station));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reviewRepository.findByUserIdAndStationId(userId, stationId))
                .thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(reviewRepository.calculateAverageRating(stationId, ReviewStatus.APPROVED))
                .thenReturn(Optional.empty());
        when(reviewRepository.countByStationIdAndStatus(stationId, ReviewStatus.APPROVED))
                .thenReturn(0L);

        var response = reviewService.createOrUpdate(
                stationId, new CreateReviewRequestDTO(2, "  "), userId);

        assertEquals(ReviewStatus.REJECTED.name(), response.status());
        assertNull(response.comment());
        assertEquals(BigDecimal.ZERO.setScale(2), station.getAverageRating());
        assertEquals(0, station.getTotalReviews());
    }

    @Test
    void rejectsCreatingReviewForMissingStationOrUser() {
        when(stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> reviewService.createOrUpdate(
                stationId, new CreateReviewRequestDTO(3, null), userId));

        when(stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE))
                .thenReturn(Optional.of(station));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> reviewService.createOrUpdate(
                stationId, new CreateReviewRequestDTO(3, null), userId));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void updatesOnlyOwnersAndRecalculatesRating() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reviewRepository.save(review)).thenReturn(review);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(reviewRepository.calculateAverageRating(stationId, ReviewStatus.APPROVED))
                .thenReturn(Optional.of(3.25));
        when(reviewRepository.countByStationIdAndStatus(stationId, ReviewStatus.APPROVED))
                .thenReturn(4L);

        var response = reviewService.updateOwn(
                reviewId, new CreateReviewRequestDTO(2, "Updated"), userId);

        assertEquals(2, response.rating());
        assertEquals("Updated", response.comment());
        assertEquals(new BigDecimal("3.25"), station.getAverageRating());
        assertEquals(4, station.getTotalReviews());
    }

    @Test
    void reportsMissingOrNonOwnedReviewsWhenUpdating() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> reviewService.updateOwn(
                reviewId, new CreateReviewRequestDTO(2, null), userId));

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        assertThrows(AccountForbiddenException.class, () -> reviewService.updateOwn(
                reviewId, new CreateReviewRequestDTO(2, null), otherUserId));
    }

    @Test
    void reportsMissingReviewAndOwnerWhenModerating() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.moderate(reviewId, ReviewStatus.REJECTED));

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.moderate(reviewId, ReviewStatus.REJECTED));
    }

    @Test
    void moderatesReviewAndRemovesItFromApprovedAggregate() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reviewRepository.save(review)).thenReturn(review);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(reviewRepository.calculateAverageRating(stationId, ReviewStatus.APPROVED))
                .thenReturn(Optional.empty());
        when(reviewRepository.countByStationIdAndStatus(stationId, ReviewStatus.APPROVED))
                .thenReturn(0L);

        var response = reviewService.moderate(reviewId, ReviewStatus.PENDING);

        assertEquals(ReviewStatus.PENDING.name(), response.status());
        assertEquals(BigDecimal.ZERO.setScale(2), station.getAverageRating());
        assertEquals(0, station.getTotalReviews());
    }

    @Test
    void deletesOwnReviewOrAllowsAdminToDeleteAnyAndRecalculatesAggregate() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(reviewRepository.calculateAverageRating(stationId, ReviewStatus.APPROVED))
                .thenReturn(Optional.of(2.5));
        when(reviewRepository.countByStationIdAndStatus(stationId, ReviewStatus.APPROVED))
                .thenReturn(1L);

        reviewService.delete(reviewId, userId, Role.ROLE_DRIVER);
        verify(reviewRepository).delete(review);
        assertEquals(new BigDecimal("2.50"), station.getAverageRating());

        reviewService.delete(reviewId, otherUserId, Role.ROLE_ADMIN);
        verify(reviewRepository, org.mockito.Mockito.times(2)).delete(review);
    }

    @Test
    void rejectsDeletingMissingReviewOrAnotherUsersReviewAsDriver() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.delete(reviewId, userId, Role.ROLE_ADMIN));

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        assertThrows(AccountForbiddenException.class,
                () -> reviewService.delete(reviewId, otherUserId, Role.ROLE_DRIVER));
    }

    @Test
    void failsIfTheStationDisappearsDuringAggregateUpdate() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(stationRepository.findById(stationId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.delete(reviewId, userId, Role.ROLE_DRIVER));
    }

    private Station station(UUID id) {
        Station result = new Station(
                "12345678901234", "Corporate", "Trade", "Brand",
                "Street", "1", "Center", "City", "SP", "00000-000",
                BigDecimal.ZERO, BigDecimal.ZERO);
        result.setId(id);
        return result;
    }

    private User user(UUID id, String name) {
        User result = new User(name, name.toLowerCase() + "@example.com", "hash");
        result.setId(id);
        return result;
    }
}

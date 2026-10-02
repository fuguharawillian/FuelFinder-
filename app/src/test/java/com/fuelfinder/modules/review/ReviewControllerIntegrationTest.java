package com.fuelfinder.modules.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.review.repository.ReviewRepository;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fuelfinder_reviews;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "jwt.secret=FuelFinderReviewIntegrationSecretKeyMustBeAtLeast32Bytes",
                "app.security.allowed-origins=http://localhost:3000,http://localhost:5500"
        })
@AutoConfigureMockMvc
class ReviewControllerIntegrationTest {

    private static final String PASSWORD = "SecurePass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        reviewRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createsUpsertsListsApprovedReviewsAndAggregatesRatings() throws Exception {
        String firstDriver = registerAndLogin("review-first@example.com", "First Driver");
        String secondDriver = registerAndLogin("review-second@example.com", "Second Driver");
        Station station = createStation();

        MvcResult first = createReview(firstDriver, station.getId(), 5, "  Great service  ")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.userName").value("First Driver"))
                .andExpect(jsonPath("$.comment").value("Great service"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();
        UUID firstReviewId = reviewId(first);

        createReview(firstDriver, station.getId(), 3, "Updated")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstReviewId.toString()))
                .andExpect(jsonPath("$.rating").value(3))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals(1, reviewRepository.count());

        createReview(secondDriver, station.getId(), 4, "Second rating")
                .andExpect(status().isCreated());
        Station updatedStation = stationRepository.findById(station.getId()).orElseThrow();
        assertEquals(new BigDecimal("3.50"), updatedStation.getAverageRating());
        assertEquals(2, updatedStation.getTotalReviews());

        mockMvc.perform(get("/stations/" + station.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.status == 'APPROVED')]").exists());
    }

    @Test
    void updatesOwnReviewAndRejectsEditingOrDeletingAnotherDriversReview() throws Exception {
        String authorToken = registerAndLogin("review-owner@example.com", "Review Owner");
        String otherToken = registerAndLogin("review-other@example.com", "Other Driver");
        Station station = createStation();
        UUID reviewId = reviewId(createReview(
                authorToken, station.getId(), 4, "Original").andReturn());

        mockMvc.perform(patch("/reviews/" + reviewId)
                        .header("Authorization", bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(2, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.comment").doesNotExist());
        assertEquals(new BigDecimal("2.00"),
                stationRepository.findById(station.getId()).orElseThrow().getAverageRating());

        mockMvc.perform(patch("/reviews/" + reviewId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(5, "Not yours")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/reviews/" + reviewId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/reviews/" + reviewId)
                        .header("Authorization", bearer(authorToken)))
                .andExpect(status().isNoContent());
        Station resetStation = stationRepository.findById(station.getId()).orElseThrow();
        assertEquals(BigDecimal.ZERO.setScale(2), resetStation.getAverageRating());
        assertEquals(0, resetStation.getTotalReviews());
    }

    @Test
    void adminCanModerateAndDeleteAnyReviewAndOnlyApprovedReviewsCount() throws Exception {
        String adminToken = createAdminAndLogin();
        String driverToken = registerAndLogin("review-moderation@example.com", "Review Driver");
        Station station = createStation();
        UUID reviewId = reviewId(createReview(
                driverToken, station.getId(), 5, "Review for moderation").andReturn());

        mockMvc.perform(patch("/reviews/" + reviewId + "/moderation")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/reviews/" + reviewId + "/moderation")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertEquals(BigDecimal.ZERO.setScale(2),
                stationRepository.findById(station.getId()).orElseThrow().getAverageRating());
        mockMvc.perform(get("/stations/" + station.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(patch("/reviews/" + reviewId + "/moderation")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(patch("/reviews/" + reviewId + "/moderation")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals(new BigDecimal("5.00"),
                stationRepository.findById(station.getId()).orElseThrow().getAverageRating());

        mockMvc.perform(delete("/reviews/" + reviewId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyAdminsCanListReviewsForModeration() throws Exception {
        String adminToken = createAdminAndLogin();
        String driverToken = registerAndLogin("review-queue@example.com", "Queue Driver");
        Station station = createStation();
        createReview(driverToken, station.getId(), 4, "Review in queue");

        mockMvc.perform(get("/admin/reviews?status=APPROVED")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/reviews?status=APPROVED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userName").value("Queue Driver"));
        mockMvc.perform(get("/admin/reviews")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/admin/reviews?status=UNKNOWN")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesReviewInputAuthenticationAndStationExistence() throws Exception {
        String driverToken = registerAndLogin("review-validation@example.com", "Review Validator");
        Station station = createStation();

        mockMvc.perform(post("/stations/" + station.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(4, "Anonymous")))
                .andExpect(status().isUnauthorized());
        createReview(driverToken, station.getId(), 0, "Invalid rating")
                .andExpect(status().isBadRequest());
        createReview(driverToken, station.getId(), 6, "Invalid rating")
                .andExpect(status().isBadRequest());
        createReview(driverToken, station.getId(), 4, "x".repeat(501))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/stations/" + UUID.randomUUID() + "/reviews")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(4, "Missing station")))
                .andExpect(status().isNotFound());

        UUID reviewId = reviewId(createReview(
                driverToken, station.getId(), 4, null).andReturn());
        mockMvc.perform(patch("/reviews/" + UUID.randomUUID())
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(4, null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/reviews/" + UUID.randomUUID())
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/reviews/" + reviewId + "/moderation")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/reviews/" + reviewId + "/moderation")
                        .header("Authorization", bearer(createAdminAndLogin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions createReview(
            String token, UUID stationId, int rating, String comment) throws Exception {
        return mockMvc.perform(post("/stations/" + stationId + "/reviews")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody(rating, comment)));
    }

    private String reviewBody(int rating, String comment) {
        String commentField = comment == null
                ? "\"comment\":null"
                : "\"comment\":\"" + comment + "\"";
        return "{\"rating\":" + rating + "," + commentField + "}";
    }

    private Station createStation() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return stationRepository.save(new Station(
                "12" + suffix,
                "Review Station Corporate",
                "Review Station",
                "Brand",
                "Main Street",
                "1",
                "Center",
                "São Paulo",
                "SP",
                "01000-000",
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    }

    private String createAdminAndLogin() throws Exception {
        User admin = new User(
                "Review Admin", "review-admin@example.com", passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ROLE_ADMIN);
        userRepository.save(admin);
        return login("review-admin@example.com");
    }

    private String registerAndLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s"}
                                """.formatted(name, email, PASSWORD)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private UUID reviewId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

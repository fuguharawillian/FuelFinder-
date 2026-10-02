package com.fuelfinder.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelfinder.modules.auth.entity.AuthSession;
import com.fuelfinder.modules.auth.entity.RefreshToken;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockCookie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fuelfinder;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "jwt.secret=FuelFinderIntegrationTestSecretKeyMustBeAtLeast32Bytes",
                "app.security.allowed-origins=http://localhost:3000,http://localhost:5500"
        })
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void initializesApplicationWithEmbeddedDatabase() {
        assertNotNull(mockMvc);
    }

    @Test
    void registrationCreatesActiveDriverWithBcryptAndHttpOnlyRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("driver@example.com", "SecurePass1!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ROLE_DRIVER"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String rawRefreshToken = result.getResponse().getCookie("refreshToken").getValue();
        User user = userRepository.findByEmail("driver@example.com").orElseThrow();
        RefreshToken storedToken = refreshTokenRepository.findAll().getFirst();

        assertEquals("ACTIVE", user.getStatus().name());
        assertTrue(passwordEncoder.matches("SecurePass1!", user.getPasswordHash()));
        assertTrue(user.getPasswordHash().startsWith("$2a$12$"));
        assertFalse(body.toString().contains(rawRefreshToken));
        assertEquals(hash(rawRefreshToken), storedToken.getTokenHash());
        assertNotEquals(rawRefreshToken, storedToken.getTokenHash());
    }

    @Test
    void rejectsDuplicateEmailAndWeakPasswords() throws Exception {
        register("duplicate@example.com", "SecurePass1!");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("duplicate@example.com", "SecurePass1!")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("weak@example.com", "weakpassword")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields.password").exists());
    }

    @Test
    void loginAuthenticatesValidCredentialsAndRejectsInvalidOrInactiveAccounts() throws Exception {
        register("login@example.com", "SecurePass1!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@example.com", "SecurePass1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().httpOnly("refreshToken", true));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@example.com", "WrongPass1!")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("missing@example.com", "WrongPass1!")))
                .andExpect(status().isUnauthorized());

        User user = userRepository.findByEmail("login@example.com").orElseThrow();
        user.setStatus(AccountStatus.BLOCKED);
        userRepository.save(user);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@example.com", "SecurePass1!")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesCookieAndReplayRevokesSession() throws Exception {
        SessionTokens tokens = register("refresh@example.com", "SecurePass1!");

        MvcResult refreshed = mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new MockCookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andReturn();
        String replacementToken = refreshed.getResponse().getCookie("refreshToken").getValue();
        assertNotEquals(tokens.refreshToken(), replacementToken);

        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new MockCookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isUnauthorized());

        AuthSession session = authSessionRepository.findAll().getFirst();
        assertNotNull(session.getRevokedAt());
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRefreshWithoutAllowedOriginAndRejectsExpiredRefreshToken() throws Exception {
        SessionTokens tokens = register("origin@example.com", "SecurePass1!");
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new MockCookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isForbidden());

        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                Instant.now().minusSeconds(1),
                hash(tokens.refreshToken()));
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new MockCookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesSessionAndExpiresRefreshCookie() throws Exception {
        SessionTokens tokens = register("logout@example.com", "SecurePass1!");
        mockMvc.perform(post("/auth/logout")
                        .header("Origin", "http://localhost:3000")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileCanBeReadAndUpdatedByItsOwner() throws Exception {
        SessionTokens tokens = register("profile@example.com", "SecurePass1!");
        register("other-profile@example.com", "SecurePass1!");

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile@example.com"));

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Updated Driver","email":"updated@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Driver"))
                .andExpect(jsonPath("$.email").value("updated@example.com"));

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"other-profile@example.com"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void driverCannotChangeAccountStatusAndAdminCanRevokeBlockedUsersSessions() throws Exception {
        SessionTokens driver = register("managed@example.com", "SecurePass1!");
        mockMvc.perform(patch("/users/" + driver.userId() + "/status")
                        .header("Authorization", "Bearer " + driver.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BLOCKED"}
                                """))
                .andExpect(status().isForbidden());

        User admin = new User("Admin", "admin@example.com", passwordEncoder.encode("AdminPass1!"));
        admin.setRole(Role.ROLE_ADMIN);
        admin = userRepository.save(admin);
        SessionTokens adminTokens = login("admin@example.com", "AdminPass1!");

        mockMvc.perform(patch("/users/" + driver.userId() + "/status")
                        .header("Authorization", "Bearer " + adminTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BLOCKED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_DRIVER"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + driver.accessToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new MockCookie("refreshToken", driver.refreshToken())))
                .andExpect(status().isUnauthorized());
        assertEquals(admin.getId(), userRepository.findByEmail("admin@example.com")
                .orElseThrow().getId());
    }

    @Test
    void revokingAllSessionsInvalidatesEveryAccessToken() throws Exception {
        SessionTokens firstSession = register("revoke@example.com", "SecurePass1!");
        SessionTokens secondSession = login("revoke@example.com", "SecurePass1!");

        mockMvc.perform(post("/auth/sessions/revoke-all")
                        .header("Authorization", "Bearer " + firstSession.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + firstSession.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + secondSession.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAccessTokenAndRejectsExpiredAccessTokens() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        SessionTokens tokens = register("expired@example.com", "SecurePass1!");
        String expiredToken = expiredAccessToken(tokens.userId(), tokens.sessionId());
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRefreshTokensForAccountsBlockedOutsideTheStatusEndpoint() throws Exception {
        SessionTokens tokens = register("blocked-refresh@example.com", "SecurePass1!");
        User blockedUser = userRepository.findById(tokens.userId()).orElseThrow();
        blockedUser.setStatus(AccountStatus.BLOCKED);
        userRepository.save(blockedUser);

        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new MockCookie("refreshToken", tokens.refreshToken())))
                .andExpect(status().isForbidden());
    }

    private SessionTokens register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return parseTokens(result);
    }

    private SessionTokens login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return parseTokens(result);
    }

    private SessionTokens parseTokens(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = response.get("accessToken").asText();
        String refreshToken = result.getResponse().getCookie("refreshToken").getValue();
        JsonNode claims = objectMapper.readTree(
                java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        return new SessionTokens(
                accessToken,
                refreshToken,
                UUID.fromString(response.get("user").get("id").asText()),
                UUID.fromString(claims.get("sid").asText()));
    }

    private String expiredAccessToken(UUID userId, UUID sessionId) {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "FuelFinderIntegrationTestSecretKeyMustBeAtLeast32Bytes"
                        .getBytes(StandardCharsets.UTF_8));
        return io.jsonwebtoken.Jwts.builder()
                .subject(userId.toString())
                .claim("role", Role.ROLE_DRIVER.name())
                .claim("sid", sessionId.toString())
                .issuedAt(java.util.Date.from(Instant.now().minusSeconds(120)))
                .expiration(java.util.Date.from(Instant.now().minusSeconds(60)))
                .signWith(key, io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();
    }

    private String registerBody(String email, String password) {
        return """
                {"fullName":"Fuel Finder Driver","email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String hash(String token) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }

    private record SessionTokens(
            String accessToken,
            String refreshToken,
            UUID userId,
            UUID sessionId) {
    }
}
